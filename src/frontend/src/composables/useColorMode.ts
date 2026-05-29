import { useDark, useToggle } from "@vueuse/core";

export function useColorMode() {
    const isDark = useDark();
    const toggleDark = useToggle(isDark);
    return { isDark, toggleDark };
}
