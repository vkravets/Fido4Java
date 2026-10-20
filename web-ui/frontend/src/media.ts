import { onBeforeUnmount, onMounted, ref } from 'vue';
export function useMedia(query: string) {
    const matches = ref(false);
    let media: MediaQueryList | undefined;
    const change = () => {
        matches.value = media?.matches || false;
    };
    onMounted(() => {
        media = window.matchMedia(query);
        change();
        media.addEventListener('change', change);
    });
    onBeforeUnmount(() => media?.removeEventListener('change', change));
    return matches;
}
