import { createApp } from 'vue';
import App from './App.vue';
import { api } from './api.ts';
import { createShortcuts } from './shortcuts.ts';
import { installShortcuts } from './shortcut-runtime.ts';
import './style.css';
import './desktop-mail.css';
import './settings.css';

const app = createApp(App);
installShortcuts(app, createShortcuts(api.profile));
app.mount('#app');
