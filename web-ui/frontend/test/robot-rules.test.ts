import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRobotEditor } from '../src/robot-rules.ts';
import { Thrift, Int64 } from '../../build/frontend-generated/runtime.mjs';
import { createBindings } from '../../build/frontend-generated/bindings.mjs';
import type { RobotRuleChange, RobotRuleInfo } from '../../build/frontend-generated/bindings.mjs';

const bindings = createBindings(Thrift, Int64);

function fixture(source = 'database') {
    let saved: RobotRuleChange | undefined;
    let deleted = '';
    const client = {
        async robotAccess() {
            return { source, peers: ['1:500/101.0'], rules: [] };
        },
        async saveRobotRule(session: string, change: RobotRuleChange) {
            assert.equal(session, 'admin-session');
            saved = change;
        },
        async deleteRobotRule(session: string, service: string, address: string) {
            assert.equal(session, 'admin-session');
            deleted = `${service}:${address}`;
        }
    };
    // Generated response structs also have serialization methods; the fake needs only data.
    const editor = createRobotEditor(
        client as Parameters<typeof createRobotEditor>[0],
        () => 'admin-session',
        bindings
    );
    return { editor, client, saved: () => saved, deleted: () => deleted };
}

function rule() {
    return {
        service: 'AreaFix',
        address: '1:500/101.0',
        masks: 'test.*',
        access: 'READ',
        enabled: true
    } as RobotRuleInfo;
}

test('new rules submit generated structs and clear passwords after saving', async () => {
    const context = fixture();
    await context.editor.refresh();
    context.editor.reset();
    Object.assign(context.editor.model, { masks: ' test.* ', password: 'new-secret' });
    await context.editor.save();
    assert.equal(context.saved()?.password, 'new-secret');
    assert.equal(context.saved()?.rule.masks, 'test.*');
    assert.ok(context.saved() instanceof bindings.RobotRuleChange);
    assert.ok(context.saved()?.rule instanceof bindings.RobotRuleInfo);
    assert.equal(context.editor.model.password, '');
});

test('editing keeps the password absent and deletion requires explicit selection', async () => {
    const context = fixture();
    await context.editor.refresh();
    context.editor.edit(rule());
    assert.equal(context.editor.editing.value, true);
    context.editor.model.enabled = false;
    await context.editor.save();
    assert.ok(!context.saved()?.password);
    assert.equal(context.saved()?.rule.enabled, false);
    await context.editor.remove();
    assert.equal(context.deleted(), '');
    context.editor.pendingDelete.value = rule();
    await context.editor.remove();
    assert.equal(context.deleted(), 'AreaFix:1:500/101.0');
    assert.equal(context.editor.pendingDelete.value, null);
});

test('file mode never sends modifications', async () => {
    const context = fixture('files');
    await context.editor.refresh();
    await assert.rejects(context.editor.save(), /файлы/);
    context.editor.pendingDelete.value = rule();
    await assert.rejects(context.editor.remove(), /файлы/);
    assert.equal(context.saved(), undefined);
    assert.equal(context.deleted(), '');
});

test('a failed save clears the entered password and keeps the policy for retry', async () => {
    const context = fixture();
    await context.editor.refresh();
    context.editor.edit(rule());
    context.editor.model.password = 'replacement';
    context.client.saveRobotRule = async () => {
        throw new Error('Connection failed');
    };
    await assert.rejects(context.editor.save(), /Connection failed/);
    assert.equal(context.editor.model.password, '');
    assert.equal(context.editor.model.masks, 'test.*');
    assert.equal(context.editor.editing.value, true);
});
