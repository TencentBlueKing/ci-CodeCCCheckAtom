<template>
    <div class="rule-set-env-selector">
        <bk-select 
            v-model="defaultValue"
            @selected="handleSelect" 
            :clearable="false"
            style="width: 404px;">
            <bk-option v-for="option in dataList"
                :key="option.id"
                :id="option.id"
                :name="option.name">
            </bk-option>
        </bk-select>
    </div>
</template>

<script>
    export default {
        name: 'rule-set-env-select',
        props: {
            name: {
                type: String,
                default: ''
            },
            value: {
                type: String,
                default: 'prod'
            },
            dataList: {
                type: Array,
                default: () => ([])
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            },
        },
        data () {
            return {
                defaultValue: 'prod'
            }
        },
        created () {
            this.defaultValue = this.value || 'prod'
        },
        methods: {
            handleSelect (value, option) {
                this.handleChange(this.name, value)
            }
        }
    }
</script>

<style lang="scss" scoped>
</style>
