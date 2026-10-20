import { provide, ref } from 'vue';
import type { AppState } from '../src/state.ts';
import { builtInMailThemes, createMailAppearance, mailAppearanceKey } from '../src/mail-themes.ts';
import { createMailTemplates, mailTemplatesKey } from '../src/mail-templates.ts';
import { profile } from './fixtures.ts';
export function mailWorkspaceFixture(dark = false) {
    const state = {
        profile: ref(profile('POINT')),
        session: ref('story'),
        busy: ref(false),
        notice: ref(''),
        error: ref(''),
        async run(operation: () => Promise<unknown>) {
            this.busy.value = true;
            this.error.value = '';
            try {
                return await operation();
            } catch (failure) {
                this.error.value = (failure as Error).message;
            } finally {
                this.busy.value = false;
            }
        }
    } as AppState;
    const appearance = createMailAppearance();
    appearance.reader.value = appearance.editor.value = dark ? 'graphite' : 'paper';
    appearance.operator.value = [
        {
            ...builtInMailThemes[2]!,
            id: 'operator-green',
            name: 'Operator green',
            colors: { ...builtInMailThemes[2]!.colors }
        }
    ];
    const templates = createMailTemplates();
    provide(mailAppearanceKey, appearance);
    provide(mailTemplatesKey, templates);
    return { state, appearance, templates };
}
export { styledMail } from './mail-text-fixture.ts';
