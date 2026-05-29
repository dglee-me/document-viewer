<script setup lang="ts">
import { ChevronRight } from "@lucide/vue";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarMenuSub,
    SidebarMenuSubButton,
    SidebarMenuSubItem,
} from "@/components/ui/sidebar";

export interface OutlineItem {
    title: string;
    dest: string | unknown[] | null;
    items: OutlineItem[];
    bold?: boolean;
    italic?: boolean;
}

defineOptions({ name: "OutlinePanel" });
defineProps<{ items: OutlineItem[]; isRoot?: boolean }>();

const emit = defineEmits<{ navigate: [dest: string | unknown[] | null] }>();

function itemClass(item: OutlineItem) {
    return [item.bold ? "font-semibold" : "", item.italic ? "italic" : ""];
}

function navigate(dest: string | unknown[] | null) {
    emit("navigate", dest);
}
</script>

<template>
    <SidebarMenu v-if="isRoot !== false">
        <SidebarMenuItem v-for="(item, i) in items" :key="i">
            <Collapsible v-if="item.items.length" class="group/collapsible">
                <CollapsibleTrigger as-child>
                    <SidebarMenuButton
                        size="sm"
                        :class="itemClass(item)"
                        @click="navigate(item.dest)"
                    >
                        <ChevronRight
                            class="transition-transform group-data-[state=open]/collapsible:rotate-90"
                        />
                        <span class="truncate">{{ item.title }}</span>
                    </SidebarMenuButton>
                </CollapsibleTrigger>

                <CollapsibleContent>
                    <SidebarMenuSub>
                        <OutlinePanel :items="item.items" :is-root="false" @navigate="navigate" />
                    </SidebarMenuSub>
                </CollapsibleContent>
            </Collapsible>

            <SidebarMenuButton
                v-else
                size="sm"
                :class="itemClass(item)"
                @click="navigate(item.dest)"
            >
                <span class="truncate">{{ item.title }}</span>
            </SidebarMenuButton>
        </SidebarMenuItem>
    </SidebarMenu>

    <template v-else>
        <SidebarMenuSubItem v-for="(item, i) in items" :key="i">
            <Collapsible v-if="item.items.length" class="group/collapsible">
                <CollapsibleTrigger as-child>
                    <SidebarMenuSubButton :class="itemClass(item)" @click="navigate(item.dest)">
                        <ChevronRight
                            class="transition-transform group-data-[state=open]/collapsible:rotate-90"
                        />
                        <span class="truncate">{{ item.title }}</span>
                    </SidebarMenuSubButton>
                </CollapsibleTrigger>

                <CollapsibleContent>
                    <SidebarMenuSub>
                        <OutlinePanel :items="item.items" :is-root="false" @navigate="navigate" />
                    </SidebarMenuSub>
                </CollapsibleContent>
            </Collapsible>

            <SidebarMenuSubButton v-else :class="itemClass(item)" @click="navigate(item.dest)">
                <span class="truncate">{{ item.title }}</span>
            </SidebarMenuSubButton>
        </SidebarMenuSubItem>
    </template>
</template>
