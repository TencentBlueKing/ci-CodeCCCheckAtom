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
                                :handle-change="handleUpdate" 
                                v-bind="atomModel[key]" 
                                :placeholder="getPlaceholder(atomModel[key], atomValue)"
                                :itemRule="{}">
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
    export default {
        name: 'shield',
        mixins: [atomMixin],
        components: {
            ItemEdit,
            CodeccAccordion
        },
        data () {
            return {
                groupList: [
                    {
                        label: '路径白名单',
                        item: ['path'],
                        desc: `以绝对路径/data/landun/workspace/CodeCCTest/cpp/为例：
扫描相对路径可输入/CodeCCTest/cpp/，只输入/cpp/不会生效
扫描某类文件如protobuffer生成的*.pb.cc，可以输入.*/.*\\.pb\\.cc
扫描工作空间中某个文件夹如P2PLive，可以输入.*/P2PLive/.* 
只扫描某个文件夹下某类文件如P2PLive下*.c，可以输入.*/P2PLive/.*\\.c
若一行中输入多个路径或路径匹配式可用英文逗号分隔
支持流水线变量`
                    },
                    {
                        label: '路径黑名单',
                        item: ['customPath'],
                        desc: `屏蔽某类文件如protobuffer生成的*.pb.cc，可以输入.*/.*\\.pb\\.cc
屏蔽所有分支中某个文件夹如P2PLive，可以输入.*/P2PLive/.* 
屏蔽某个文件夹下某类文件如P2PLive下*.c，可以输入.*/P2PLive/.*\\.c
若一行中输入多个路径匹配式可用英文逗号分隔
支持流水线变量`
                    }
                ]
            }
        },
        methods: {
            handleUpdate (name, value) {
                this.atomValue[name] = value
            }
        }
    }
</script>