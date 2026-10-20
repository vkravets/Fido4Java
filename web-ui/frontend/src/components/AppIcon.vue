<script setup lang="ts">
const paths = {
    mail: 'M3 5h18v14H3z M3 6l9 7 9-7',
    echo: 'M3 5h18v14H3z M3 6l9 7 9-7 M7 3h10',
    file: 'M6 3h8l4 4v14H6z M14 3v5h4 M9 12h6 M9 16h6',
    templates: 'M8 3h9l4 4v12H8z M17 3v5h4 M4 7v14h12 M11 12h7 M11 15h5',
    settings: 'M9 3h6l1 4 4 2v6l-4 2-1 4H9l-1-4-4-2V9l4-2z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
    link: 'M10 14l4-4 M8 16l-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0 M16 8l1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0',
    clock: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 6v6l4 2',
    logs: 'M8 6h13 M8 12h13 M8 18h13 M3 6h1 M3 12h1 M3 18h1',
    user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2z',
    users: 'M14 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M2 21v-2a8 8 0 0 1 16 0v2 M18 4a4 4 0 0 1 0 8 M19 15a6 6 0 0 1 4 6',
    directory: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M3 12h18 M12 3c5 5 5 13 0 18-5-5-5-13 0-18',
    routes: 'M4 5h12l-3-3 M16 5l-3 3 M20 19H8l3-3 M8 19l3 3 M4 5v10 M20 19V9',
    robot: 'M5 7h14v13H5z M12 3v4 M8 12h1 M15 12h1 M9 16h6 M2 11v5 M22 11v5',
    reply: 'M9 5l-7 6 7 6v-4c7 0 10 2 13 7-1-9-5-12-13-12z',
    forward: 'M15 5l7 6-7 6v-4C8 13 5 15 2 20 3 11 7 8 15 8z',
    left: 'M19 12H5 M10 7l-5 5 5 5',
    right: 'M5 12h14 M14 7l5 5-5 5',
    down: 'M6 9l6 6 6-6',
    collapse: 'M14 6l-6 6 6 6 M20 6l-6 6 6 6',
    expand: 'M4 6l6 6-6 6 M10 6l6 6-6 6',
    search: 'M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0 M15 15l6 6',
    refresh: 'M20 7A9 9 0 1 0 21 15 M20 2v6h-6',
    edit: 'M4 16l12-12 4 4L8 20H4z M14 6l4 4',
    send: 'M3 3l19 9-19 9 4-9z M7 12h15',
    trash: 'M3 6h18 M9 6V3h6v3 M6 6l1 15h10l1-15 M10 10v7 M14 10v7',
    sliders: 'M3 6h18 M3 12h18 M3 18h18 M7 3v6 M16 9v6 M10 15v6',
    save: 'M4 3h13l4 4v14H3V3z M7 3v6h10V3 M7 21v-8h10v8',
    close: 'M6 6l12 12 M18 6L6 18',
    eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
    sun: 'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M12 1v3 M12 20v3 M1 12h3 M20 12h3 M4 4l2 2 M18 18l2 2 M4 20l2-2 M18 6l2-2',
    moon: 'M21 13a9 9 0 0 1-10-10 9 9 0 1 0 10 10',
    logout: 'M9 3H3v18h6 M9 12h13 M17 7l5 5-5 5'
} as const;
defineProps<{ name: keyof typeof paths }>();
</script>
<template>
    <svg
        class="app-icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        focusable="false"
    >
        <path :d="paths[name]" />
    </svg>
</template>
<style>
.app-icon {
    width: 18px;
    height: 18px;
    flex: 0 0 18px;
    vertical-align: middle;
}
</style>
