<template>
    <div class="item-edit">
        <ul>
            <template v-if="paramList.length">
                <li class="param-item" v-for="(param, index) in paramList" :key="index">
                    <!-- <div class="bk-form-item">
                        <bk-input :disabled="disabled" :value="param" @change="(value) => handleParamChange(value, index)" v-validate.initial="`required`"/>
                    </div> -->
                    <form-field :is-error="errors.has(`param-${index}.path`)" :error-msg="errors.first(`param-${index}.path`)">
                        <vuex-input
                            :data-vv-scope="`param-${index}`"
                            :disabled="disabled"
                            :handle-change="(name, value) => handleParamChange(value, index)"
                            v-validate.initial="itemRule"
                            name="path"
                            :value="param" />
                    </form-field>
                    <i @click.stop.prevent="editParam(index, false)" class="bk-icon icon-minus hover-click " v-if="!disabled" />
                </li>
                
            </template>
            <a class="text-link hover-click" v-if="!disabled" @click.stop.prevent="editParam(paramList.length, true)">
                <i class="bk-icon icon-plus-circle" />
                <span>{{ addBtnText }}</span>
            </a>
        </ul>
    </div>
</template>

<script>

    export default {
        name: 'params',
        props: {
            name: {
                type: String,
                default: ''
            },
            value: {
                type: Array,
                default: () => ([])
            },
            addBtnText: {
                type: String,
                default: '新增路径'
            },
            disabled: {
                type: Boolean,
                default: false
            },
            // 为true允许数组为空，为false表示至少留一项
            allowNull: {
                type: Boolean,
                default: true
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            },
            itemRule: {
                type: [String, Object],
                default: () => ({})
            }
        },
        data () {
            return {
                paramList: [],
                keyRules: [
                    { required: true, message: 'Key不允许为空', trigger: 'blur' }
                ]
            }
        },
        watch: {
            value (val) {
                this.paramList = val
            }
        },
        async created () {
            if (this.value && typeof this.value === 'string') {
                this.paramList = [this.value]
            } else {
                this.paramList = this.value || []
            }
        },
        methods: {
            editParam (index, isAdd) {
                if (isAdd) {
                    if (this.paramList.length && this.paramList.some(item => !item)) {
                        return
                    }
                    this.paramList.splice(index + 1, 0, '')
                } else {
                    // 如果不允许数组为空并且是剩余最后一项，则不允许删除
                    if (this.allowNull || this.paramList.length > 1) {
                        this.paramList.splice(index, 1)
                    }
                }
                this.handleChange(this.name, this.paramList)
            },
            handleParamChange (value, paramIndex) {
                this.paramList[paramIndex] = value
                this.handleChange(this.name, this.paramList)
            }
        }
    }
</script>

<style lang="scss">
    .item-edit {
        .param-item {
            display: flex;
            align-items: flex-start;
            margin-bottom: 10px;
            .bk-form-item {
                flex: 1;
                margin-right: 8px;
                .bk-form-content {
                    width: 100%;
                }
            }
        }
        .hover-click {
            cursor: pointer;
            line-height: 32px;
            color: #3a84ff;
            font-size: 12px;
        }
    }
</style>
