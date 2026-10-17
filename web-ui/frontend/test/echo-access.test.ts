import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createEchoEditor } from '../src/echo-access.ts';
import type { EchoSubscriptionInfo } from '../../build/frontend-generated/bindings.mjs';

function fixture(source = 'database') {
    const subscriptions: { peer: string; area: string; access: string }[] = [];
    const calls: string[][] = [];
    let fail = false;
    const client = {
        async echoAccess() {
            return { source, peers: ['1:500/101.0'], areas: [], subscriptions: [...subscriptions] };
        },
        async subscribe(session: string, peer: string, area: string, access: string) {
            if (fail) throw new Error('Storage failed');
            calls.push([session, peer, area, access]);
            subscriptions.splice(0, subscriptions.length, { peer, area, access });
        },
        async unsubscribe(session: string, peer: string, area: string) {
            calls.push([session, peer, area]);
            subscriptions.length = 0;
        }
    };
    const editor = createEchoEditor(
        client as Parameters<typeof createEchoEditor>[0],
        () => 'admin'
    );
    return {
        editor,
        calls,
        fail: () => {
            fail = true;
        }
    };
}

test('grants receive-only access by default and refreshes persisted permissions', async () => {
    const { editor, calls } = fixture();
    await editor.refresh();
    editor.reset();
    editor.model.area = ' NEW.ECHO ';
    await editor.save();
    assert.deepEqual(calls, [['admin', '1:500/101.0', 'NEW.ECHO', 'WRITE']]);
    assert.equal(editor.configuration.value?.subscriptions[0]?.access, 'WRITE');
    editor.edit(editor.configuration.value!.subscriptions[0]!);
    editor.model.access = 'BOTH';
    await editor.save();
    assert.equal(editor.configuration.value?.subscriptions[0]?.access, 'BOTH');
    assert.equal(editor.editing.value, false);
});

test('revocation requires an explicit selection and retains edit state on save failure', async () => {
    const { editor, calls, fail } = fixture();
    await editor.refresh();
    editor.reset();
    await editor.remove();
    assert.equal(calls.length, 0);
    const subscription = {
        peer: '1:500/101.0',
        area: 'test',
        access: 'READ'
    } as EchoSubscriptionInfo;
    editor.pendingDelete.value = subscription;
    await editor.remove();
    assert.deepEqual(calls, [['admin', subscription.peer, subscription.area]]);
    editor.edit(subscription);
    fail();
    await assert.rejects(editor.save(), /Storage failed/);
    assert.equal(editor.editing.value, true);
    assert.equal(editor.model.area, 'test');
});

test('file mode and invalid input never send modifications', async () => {
    const readonly = fixture('config');
    await readonly.editor.refresh();
    await assert.rejects(readonly.editor.save(), /configuration files/);
    await assert.rejects(readonly.editor.remove(), /configuration files/);
    assert.equal(readonly.calls.length, 0);
    const { editor, calls } = fixture();
    await editor.refresh();
    editor.reset();
    await assert.rejects(editor.save(), /Select a link/);
    editor.model.area = 'test';
    editor.model.access = 'INVALID';
    await assert.rejects(editor.save(), /valid access rights/);
    assert.equal(calls.length, 0);
});
