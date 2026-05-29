<script setup lang="ts">
import type { CollapsibleContentEmits, CollapsibleContentProps } from "reka-ui";
import { CollapsibleContent, useForwardPropsEmits } from "reka-ui";

const props = defineProps<CollapsibleContentProps>();
const emits = defineEmits<CollapsibleContentEmits>();

const forwarded = useForwardPropsEmits(props, emits);
</script>

<template>
    <CollapsibleContent data-slot="collapsible-content" v-bind="forwarded">
        <slot />
    </CollapsibleContent>
</template>
