<template>
    <div class="author-transfer">
        <ul>
            <template v-if="paramList.length">
                <li class="param-item" v-for="(param, index) in paramList" :key="index" :isError="errors.any(`param-${index}`)">
                    <form-field :is-error="errors.has(`param-${index}.sourceAuthor`)" :error-msg="errors.first(`param-${index}.sourceAuthor`)">
                        <vuex-input
                            :data-vv-scope="`param-${index}`"
                            :disabled="disabled"
                            :handle-change="(name, value) => handleParamChange(name, value, index)"
                            v-validate.initial="`required`"
                            name="sourceAuthor"
                            placeholder="原处理人"
                            :value="param.sourceAuthor" />
                    </form-field>
                    <form-field :is-error="errors.has(`param-${index}.targetAuthor`)" :error-msg="errors.first(`param-${index}.targetAuthor`)">
                        <bk-member-selector 
                            :data-vv-scope="`param-${index}`"
                            name="targetAuthor"
                            placeholder="目标处理人" 
                            :disabled="disabled" 
                            v-validate.initial="`required`"
                            :value="param.targetAuthor.split(',')" 
                            @change="(value) => handleParamChange('targetAuthor', value.join(','), index)"
                        />
                    </form-field>
                    <i @click.stop.prevent="editParam(index, false)" class="bk-icon icon-minus hover-click" v-if="!disabled" />
                </li>
            </template>
            <a class="text-link hover-click" v-if="!disabled" @click.stop.prevent="editParam(paramList.length, true)">
                <i class="bk-icon icon-plus-circle" />
                <span>添加处理人转换</span>
            </a>
        </ul>
    </div>
</template>

<script>
    export default {
        name: 'author-transfer',
        props: {
            name: {
                type: String,
                default: ''
            },
            value: {
                type: Array,
                default: () => ([])
            },
            disabled: {
                type: Boolean,
                default: false
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            }
        },
        data () {
            return {
                paramList: []
            }
        },
        watch: {
            value (val) {
                this.paramList = val
            }
        },
        async created () {
            this.paramList = this.value
        },
        methods: {
            editParam (index, isAdd) {
                if (isAdd) {
                    const param = {
                        sourceAuthor: '',
                        targetAuthor: ''
                    }
                    this.paramList.splice(index + 1, 0, param)
                } else {
                    this.paramList.splice(index, 1)
                }
                this.handleChange(this.name, this.paramList)
            },
            handleParamChange (key, value, paramIndex) {
                const param = this.paramList[paramIndex]
                if (param) {
                    Object.assign(param, {
                        [key]: value
                    })
                    this.handleChange(this.name, this.paramList)
                }
            }
        }
    }
</script>

<style lang="scss">
    .author-transfer {
        ul {
            padding-left: 0px;
        }
        .param-item {
            display: flex;
            // justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 10px;
            > span {
                flex: 1;
                margin-right: 0 10px;
            }
            > div {
                flex: 1;
                margin-right: 10px;
            }
            > .bk-form-item {
                margin-top: 0px !important;
            }
        }
        .param-item-empty {
            text-align: center;
            color: #c4c6cc;
        }
        .hover-click {
            cursor: pointer;
            line-height: 36px;
            color: #3a84ff;
            font-size: 12px;
        }
    }
</style>
