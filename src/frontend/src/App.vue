<script setup lang="ts">
import "vue-sonner/style.css";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
</script>

<template>
    <TooltipProvider>
        <RouterView />
        <Toaster position="bottom-right" />
    </TooltipProvider>
</template>
