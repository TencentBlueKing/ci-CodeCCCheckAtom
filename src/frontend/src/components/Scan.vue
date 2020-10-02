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

        <form-field v-if="atomValue.toolScanType === '2'" key="mrCommentEnable" class="head-level" :desc="atomModel.mrCommentEnable.desc" :required="atomModel.mrCommentEnable.required" :label="atomModel.mrCommentEnable.label" :is-error="errors.has('mrCommentEnable')" :error-msg="errors.first('mrCommentEnable')">
            <component 
                :is="atomModel.mrCommentEnable.type" 
                name="mrCommentEnable"
                :value="atomValue.mrCommentEnable" 
                :handle-change="handleUpdate" 
                :text="atomModel.mrCommentEnable.text"
            >
            </component>
        </form-field>

        <form-field key="newDefectJudgeFromDate" class="head-level" :desc="atomModel.newDefectJudgeFromDate.desc" :required="atomModel.newDefectJudgeFromDate.required" :label="atomModel.newDefectJudgeFromDate.label" :is-error="errors.has('newDefectJudgeFromDate')" :error-msg="errors.first('newDefectJudgeFromDate')">
            <bk-date-picker class="mr15" name="newDefectJudgeFromDate" :value="atomValue.newDefectJudgeFromDate" placeholder="选择日期" @change="handleSelectDate"></bk-date-picker>
            <span style="color: #666;font-size: 12px;">之后产生的告警为新告警</span>
        </form-field>

        <form-field key="transferAuthorList" class="head-level" :desc="atomModel.transferAuthorList.desc" :required="atomModel.transferAuthorList.required" :label="atomModel.transferAuthorList.label" :is-error="errors.has('transferAuthorList')" :error-msg="errors.first('transferAuthorList')">
            <component 
                :is="atomModel.transferAuthorList.type" 
                name="transferAuthorList"
                :value="atomValue.transferAuthorList" 
                :handle-change="handleUpdate"
            >
            </component>
        </form-field>
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