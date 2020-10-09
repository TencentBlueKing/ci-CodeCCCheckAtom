<template>
    <div class="task-content" :style="{height: extraHeight + 'px'}">
        <div class="atom-txt"><i class="bk-icon icon-exclamation-circle-shape"></i>如流水线配置了质量红线请谨慎使用异步功能，可能会由于结果异步输出导致红线拦截</div>
        <template v-for="(obj, key) in asyncModel">
            <form-field
                class="head-level" 
                :inline="obj.inline" 
                :key="key" 
                :desc="obj.desc" 
                :required="obj.required" 
                :label="obj.label" 
                :is-error="errors.has(key)" 
                :error-msg="errors.first(key)">
                <component 
                    :is="obj.type" 
                    :atom-value="atomValue"
                    :name="key"
                    :placeholder="getPlaceholder(obj, atomValue)"
                    :value="atomValue[key]" 
                    :display="atomValue[`${key}Display`]"
                    :handle-change="handleUpdate" 
                    v-bind="obj" 
                    v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })">
                </component>
            </form-field>
        </template>
    </div>
</template>

<script>
    import { atomMixin }from 'bkci-atom-components'
    import { mapState } from 'vuex'
    import TaskSelect from '@/components/TaskSelect'

    export default {
        name: 'async',
        mixins: [atomMixin],
        components: {
            TaskSelect
        },
        data () {
            return {

            }
        },
        computed: {
            ...mapState([
                'extraHeight'
            ]),
            asyncModel () {
                const model = Object.keys(this.atomModel).reduce((model, obj) => {
                    if (this.atomModel[obj] && this.atomModel[obj].tabName === 'async') {
                        model = Object.assign(model, { [obj]: this.atomModel[obj]})
                    }
                    return model
                }, {})
                return model
            }
        },
        created () {
        },
        methods: {
            handleUpdate (name, value) {
                this.atomValue[name] = value
            }
        }
    }
</script>

<style>

</style>