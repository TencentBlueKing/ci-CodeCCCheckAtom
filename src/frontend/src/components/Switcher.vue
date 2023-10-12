<template>
    <bk-switcher
        type="number"
        :name="name"
        :value="value"
        v-bind="restProps"
        @change="handleUpdate">
    </bk-switcher>
</template>

<script>
    export default {
        name: 'switcher',
        props: {
            name: {
                type: String,
                required: true
            },
            value: [Boolean],
            isError: {
                type: Boolean,
                default: false
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            }
        },
        components: {
        },
        data () {
            return {

            }
        },
        computed: {
            restProps () {
                const { name, value, ...restProps } = this.$attrs
                return restProps
            },
        },
        created () {
        },
        methods: {
            handleUpdate(value) {
                this.handleChange(this.name, value)
            }
        }
    }
</script>

<style>

</style>