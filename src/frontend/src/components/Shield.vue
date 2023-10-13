<template>
    <section>
        <template v-for="(prop, index) in groupList">
            <codecc-accordion show-checkbox show-content :key="index" :label="prop.label" :labelDesc="prop.desc">
                <div slot="content" class="bk-form">
                    <template v-for="key of prop.item">
                        <form-field v-if="!atomModel[key].hidden" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)">
                            <component 
                                :is="atomModel[key].type"
                                :name="key"
                                v-validate.initial="Object.assign({}, atomModel[key].rule, { required: !!atomModel[key].required })"
                                :value="atomValue[key]" 
                                :disabled="atomModel[key].disabled"
                                :handle-change="handleUpdate" 
                                v-bind="atomModel[key]" 
                                :placeholder="getPlaceholder(atomModel[key], atomValue)"
                                :item-rule="atomModel[key].rule">
                            </component>
                        </form-field>
                    </template>
                </div>
            </codecc-accordion>
        </template>
    </section>
</template>

<script>
    import { atomMixin }from 'bkci-atom-components'
    import ItemEdit from './ItemEdit'
    import CodeccAccordion from './CodeccAccordion'
    import DEPLOY_ENV from '@/constants/env';
    
    export default {
        name: 'shield',
        mixins: [atomMixin],
        components: {
            ItemEdit,
            CodeccAccordion
        },
        data () {
            const pathUrl = DEPLOY_ENV === 'tencent' ? `\n<a href='${window.IWIKI_SITE_URL}/p/679617074' target='_blank' style='color: #3a84ff'>${this.$t('了解更多')}>></a>` : ''
            return {
                groupList: [
                    {
                        label: this.$t('路径白名单'),
                        item: ['path'],
                        desc: this.$t('路径白名单描述') + pathUrl
                    },
                    {
                        label: this.$t('路径黑名单'),
                        item: ['customPath'],
                        desc: this.$t('路径黑名单描述') + pathUrl
                    },
                    {
                        label: 'YAML',
                        item: ['scanTestSource'],
                        desc: this.$t('yaml描述')
                    }
                ]
            }
        },
        watch: {
            'atomValue.checkerSetType' (value) {
                if (value === 'epcScan') {
                    this.atomModel.scanTestSource && (this.atomModel.scanTestSource.disabled = true)
                } else {
                    this.atomModel.scanTestSource && (this.atomModel.scanTestSource.disabled = false)
                }
            }
        },
        methods: {
            handleUpdate (name, value) {
                this.atomValue[name] = value
            }
        }
    }
</script>