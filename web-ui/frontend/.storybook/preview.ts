import type { Preview } from '@storybook/vue3-vite';
import { setup } from '@storybook/vue3-vite';
import { directorySearchKey } from '../src/directory.ts';
import { setLocale } from '../src/i18n.ts';
import '../src/style.css';
import '../src/desktop-mail.css';
import './preview.css';
setup((app) =>
    app.provide(directorySearchKey, async () => ({ items: [], nextOffset: 0, hasMore: false }))
);

const preview: Preview = {
    initialGlobals: { theme: 'light', locale: 'en' },
    globalTypes: {
        theme: {
            description: 'JFtn theme',
            toolbar: { icon: 'circlehollow', items: ['light', 'dark'], dynamicTitle: true }
        },
        locale: {
            description: 'Interface language',
            toolbar: {
                icon: 'globe',
                items: [
                    { value: 'en', title: 'English' },
                    { value: 'uk', title: 'Українська' }
                ],
                dynamicTitle: true
            }
        }
    },
    parameters: { layout: 'fullscreen' },
    decorators: [
        (story, context) => {
            document.documentElement.dataset.theme =
                context.globals.theme === 'dark' ? 'dark' : 'light';
            setLocale(context.globals.locale === 'uk' ? 'uk' : 'en');
            return {
                components: { story },
                template: `<div class="storybook-stage">
                <Teleport to="body"><section id="notifications" class="messages" aria-label="Notifications"></section></Teleport>
                <story />
            </div>`
            };
        }
    ]
};

export default preview;
