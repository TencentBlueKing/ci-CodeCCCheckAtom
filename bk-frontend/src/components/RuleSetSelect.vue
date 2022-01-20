<template>
    <div class="rule-set-selector">
        <div class="rule-set-input" @click="openSelect">
            <p class="rule-set-value" :title="getValueShow(renderList)">{{ getValueShow(renderList) }}</p>
            <span class="placeholder" v-if="!renderList.length">请选择</span>
            <span class="bk-select-clear bk-icon icon-close-circle-shape" @click.stop="handleClear"></span>
        </div>
        <rule-set-dialog
            :visiable.sync="dialogVisiable"
            :curLang="curRule"
            :selected-list="renderList"
            :default-lang="defaultQueryLang"
            :handle-select="handleSelect"
            :get-rule-set-list="getRuleSetList"
        ></rule-set-dialog>
    </div>
</template>

<script>
    import { mapState } from 'vuex'
    import RuleSetDialog from './RuleSetDialog'
    import { getQueryParams } from '@/utils/util'

    export default {
        name: 'rule-set-select',
        components: {
            RuleSetDialog
        },
        props: {
            name: {
                type: String,
                default: ''
            },
            value: {
                type: Array,
                default: () => ([])
            },
            dataList: {
                type: Array,
                default: () => ([])
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            },
            getRuleSetList: Function
        },
        data () {
            return {
                dialogVisiable: false,
                defaultQueryLang: [],
                renderList: []
            }
        },
        computed: {
            ...mapState([
                'toolMeta'
            ]),
            curRule () {
                const fullKey =  this.name && this.name.replace('_RULE', '')
                const curLang = this.toolMeta.LANG.find(lang => lang.langFullKey === fullKey)
                return curLang.name || ''
            }
        },
        watch: {
            dataList (newVal) {
                if (this.value && this.value.length) {
                    this.value.forEach(val => {
                        const temp = newVal.filter(item => item.checkerSetId === val) || []
                        if (temp.length && !this.renderList.find(item => item.checkerSetId === val)) {
                            this.renderList.push(temp[0])
                        }
                    })
                }
            }
        },
        created () {
            this.renderRuleSetList()
        },
        methods: {
            async renderRuleSetList () {
                if (!this.value || !this.value.length) {
                    const defaultRuleSet = this.dataList.filter(item => item.checkerSetLang === this.curRule && item.defaultCheckerSet)
                    if (defaultRuleSet.length) {
                        const newVal = [].concat(defaultRuleSet.map(val => val.checkerSetId))
                        this.renderList = defaultRuleSet
                        this.$emit('input', newVal)
                        this.handleChange(this.name, newVal)
                    }
                } else {
                    this.value.forEach(val => {
                        const temp = this.dataList.filter(item => item.checkerSetId === val) || []
                        temp.length && this.renderList.push(temp[0])
                    })
                }
            },
            handleSelect (checkerSet, isCancel) {
                if (isCancel) {
                    this.renderList = this.renderList.filter(item => item.checkerSetId !== checkerSet.checkerSetId)
                } else {
                    this.renderList.push(checkerSet)
                }

                let newVal = this.renderList.map(item => item.checkerSetId)
                this.$emit('input', newVal)
                this.handleChange(this.name, newVal)
            },
            handleClear () {
                this.renderList = []
                this.$emit('input', [])
                this.handleChange(this.name, [])
            },
            getValueShow (list) {
                const nameList = list.map(val => val.checkerSetName)
                return nameList.join(',')
            },
            openSelect () {
                this.defaultQueryLang = [this.curRule]
                this.dialogVisiable = true
            }
        }
    }
</script>

<style lang="scss" scoped>
    .rule-set-input {
        position: relative;
        padding: 0 20px 0 10px;
        border: 1px solid #c4c6cc;
        border-radius: 2px;
        width: 404px;
        height: 32px;
        line-height: 30px;
        color: #63656e;
        cursor: pointer;
        font-size: 12px;
        overflow: hidden;
        .rule-set-value {
            margin: 0;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .placeholder {
            color: #c9d2db;
        }
        .bk-select-clear {
            display: none;
            position: absolute;
            right: 6px;
            top: 8px;
            text-align: center;
            font-size: 14px;
            z-index: 100;
            color: #c4c6cc;
            &:hover {
                color: #979ba5;
            }
        }
        &:hover {
            .bk-select-clear {
                display: inline-block;
            }
        }
    }
    .checker-select {
        width: 402px;
    }
</style>
