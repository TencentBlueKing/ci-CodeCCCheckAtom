<template>
    <section>
        <form-field 
            key="toolScanType" 
            class="head-level" 
            :desc="atomModel.toolScanType.desc" 
            :descLink="atomModel.toolScanType.descLink"
            :descLinkText="atomModel.toolScanType.descLinkText"
            :required="atomModel.toolScanType.required" 
            :label="atomModel.toolScanType.label" 
            :is-error="errors.has('toolScanType')" 
            :error-msg="errors.first('toolScanType')">
            <component 
                :is="atomModel.toolScanType.type" 
                name="toolScanType"
                :value="atomValue.toolScanType" 
                :handle-change="handleUpdate" 
                :list="atomModel.toolScanType.list"
            >
            </component>
        </form-field>

        <template v-for="(obj, key) in customTabModel">
            <form-field class="head-level"
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
                    v-bind="obj" 
                    v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })"
                >
                </component>
            </form-field>
        </template>

        <form-field key="transferAuthorList" class="head-level" :desc="atomModel.transferAuthorList.desc" :required="atomModel.transferAuthorList.required" :label="atomModel.transferAuthorList.label" :is-error="errors.has('transferAuthorList')" :error-msg="errors.first('transferAuthorList')">
            <component 
                :is="atomModel.transferAuthorList.type" 
                name="transferAuthorList"
                :value="atomValue.transferAuthorList" 
                :handle-change="handleUpdate"
            >
            </component>
        </form-field>
        <template v-for="(obj, key) in scanTabModel">
            <form-field class="head-level"
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
                    v-bind="obj" 
                    v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })"
                >
                </component>
            </form-field>
        </template>
    </section>
</template>

<script>
    import { atomMixin }from 'bkci-atom-components'
    import AuthorTransfer from './AuthorTransfer'
    import RadioGroup from './RadioGroup'
    export default {
        name: 'scan',
        mixins: [atomMixin],
        components: {
            AuthorTransfer,
            RadioGroup
        },
        computed: {
            scanTabModel () {
                const model = Object.keys(this.atomModel).reduce((model, obj) => {
                    if (this.atomModel[obj] && this.atomModel[obj].tabName === 'scan') {
                        model = Object.assign(model, { [obj]: this.atomModel[obj]})
                    }
                    return model
                }, {})
                return model
            },
            customTabModel () {
                const tabMap = {
                    '2': 'mrpr',
                    '6': 'diffbr'
                }
                const tabName = tabMap[this.atomValue.toolScanType]
                if (!tabName) return {}
                const model = Object.keys(this.atomModel).reduce((model, obj) => {
                    if (this.atomModel[obj] && this.atomModel[obj].tabName === tabName) {
                        model = Object.assign(model, { [obj]: this.atomModel[obj]})
                    }
                    return model
                }, {})
                return model
            }
        },
        created () {
            if (this.atomValue.mrCommentEnable === undefined) {
                this.atomValue.mrCommentEnable = true
            }
        },
        methods: {
            handleSelectDate (value) {
                this.handleUpdate('newDefectJudgeFromDate', value)
            },
            handleUpdate (name, value) {
                this.atomValue[name] = value
            }
        }
    }
</script>

<style scoped>
    :deep() .bkdevops-radio {
        line-height: 30px;
    }
</style>