<template>
    <bk-input
        type="number"
        :name="name"
        :value="value"
        v-bind="restProps"
        @input="handleInput">
    </bk-input>
</template>

<script>
    export default {
        name: 'input-number',
        props: {
            name: {
                type: String,
                required: true
            },
            value: [Number],
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
            handleInput(value) {
                this.handleChange(this.name, Number(value))
            }
        }
    }
</script>

<style>

</style>