<template>
    <bk-radio-group @change="handleSelect" :value="value" :name="name">
        <bk-radio v-for="item in list" :key="item.id" class="bkdevops-radio" :value="item.value" :disabled="disabled || item.disabled">
            {{ item.label }}
            <i v-if="handleRedPoint(item.value)" class="red-point"></i>
        </bk-radio>
    </bk-radio-group>
</template>

<script>
    export default {
        name: 'radio-group',
        props: {
            name: {
                type: String,
                required: true
            },
            value: {
                type: [String, Array],
                required: true,
                default: ''
            },
            disabled: {
                type: Boolean,
                default: false
            },
            list: {
                type: Array,
                default: () => ([])
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            }
        },
        watch: {
            value (value, oldValue) {
                value !== oldValue && this.$emit('input', value)
            }
        },
        methods: {
            handleSelect (value) {
                if (value === 'true') {
                    value = true
                } else if (value === 'false') {
                    value = false
                }
                this.handleChange(this.name, value)
            },
            handleRedPoint (value) {
                return value === '2' && !window.localStorage.getItem('mr-20200702')
            }
        }
    }
</script>

<style lang="scss" scoped>
    .bkdevops-radio {
        margin-right: 30px;
    }
</style>
