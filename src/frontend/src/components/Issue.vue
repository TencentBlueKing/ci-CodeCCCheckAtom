<template>
    <section>
        <template v-for="(obj, key) in issueTabModel">
            <form-field class="head-level issue-field"
                v-if="atomModel[key] && rely(atomModel[key], atomValue)" 
                :key="key" 
                :desc="obj.desc" 
                :inline="obj.inline" 
                :required="obj.required" 
                :label="obj.label" 
                :is-error="errors.has(key)" 
                :error-msg="errors.first(key)">
                <component 
                    :is="obj.type" 
                    :atom-value="atomValue"
                    :name="key"
                    :value="atomValue[key]" 
                    :handle-change="handleUpdate" 
                    :issue-info="issueInfo"
                    :refresh-oauth="init"
                    v-bind="obj" 
                    v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })">
                </component>
            </form-field>
        </template>
    </section>
</template>

<script>
    import { atomMixin }from 'bkci-atom-components'
    import { mapState } from 'vuex'
    import InputNumber from './InputNumber'
    import Switcher from './Switcher'
    import IssueSystem from './IssueSystem'

    export default {
        name: 'issue',
        mixins: [atomMixin],
        components: {
            InputNumber,
            Switcher,
            IssueSystem
        },
        data () {
            return {
                issueInfo: {}
            }
        },
        computed: {
            ...mapState(['toolList']),
            issueTabModel () {
                const model = Object.keys(this.atomModel).reduce((model, obj) => {
                    if (this.atomModel[obj] && this.atomModel[obj].tabName === 'issue') {
                        model = Object.assign(model, { [obj]: this.atomModel[obj]})
                    }
                    return model
                }, {})
                return model
            }
        },
        watch: {
            'atomValue.tools'(value) {
                this.handleIssueTools()
            },
            'atomValue.beAutoLang'(value) {
                this.handleIssueTools()
            }
        },
        created () {
            this.init()
        },
        methods: {
            handleUpdate (name, value) {
                this.atomValue[name] = value
            },
            async init() {
                const issueInfo = await this.$store.dispatch('getTaskIssue') || {}
                const { multiPipelineMark } = this.atomValue
                const list = issueInfo.issueSystemInfoVOList || []
                list.forEach(item => {
                    item.value = item.system
                    item.label = item.detail
                })
                // 提单类型列表
                this.atomModel.issueSystem.list = list
                // 接口有tapd相关信息，以接口为准，否则从atomValue取值，兼容复制流水线情况
                if (issueInfo.subSystemId) {
                    this.issueInfo = issueInfo
                    const { system, subSystem, subSystemId, subSystemCn } = issueInfo
                    if (!this.atomValue.issueSubSystemId) {
                        this.atomValue.issueSystem = system
                        this.atomValue.issueSubSystem = subSystem
                        this.atomValue.issueSubSystemId = subSystemId
                        this.atomValue.issueSubSystemCn = subSystemCn
                    }
                } else {
                    const { issueSystem, issueSubSystem, issueSubSystemId, issueSubSystemCn } = this.atomValue
                    this.issueInfo = {
                        system: issueSystem,
                        subSystem: issueSubSystem,
                        subSystemId: issueSubSystemId,
                        subSystemCn: issueSubSystemCn,
                        issueSystemInfoVOList: list
                    }
                }
            },
            handleIssueTools() {
                let tools = JSON.parse(JSON.stringify(this.atomModel.tools && this.atomModel.tools.list))
                tools = tools.filter(item => this.atomValue.tools.includes(item.id) && !['CLOC', 'STAT', 'SCC', 'DUPC'].includes(item.id))
                tools.unshift({id: 'ALL', name: this.$t('所有工具')})
                // 提单工具
                this.atomModel.issueTools.options = tools
            }
        }
    }
</script>

<style>
    .issue-field .bk-select-name {
        width: 400px!important;
    }
</style>