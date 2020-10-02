<template>
    <div class="remote-atom" id="public-atom" v-resize="resize" :class="{ 'atom-disabled': atomDisabled}">
        <main class="app-container">
            <Atom
                v-if="hasInitData"
                :atom-props-value="atomPropsValue" 
                :atom-props-model="atomPropsModel"
                :atom-props-container-info="containerInfo"
                :atom-props-disabled="atomDisabled"
                :current-user-info="currentUserInfo">
            </Atom>
        </main>
    </div>
</template>

<script>
    import Vue from 'vue'
    import Atom from '../Atom'
    import { mapState } from 'vuex'
    export default {
        name: 'public-atom',
        components: {
            Atom
        },
        directives: {
            resize: {
                bind(el, binding) { // el为绑定的元素，binding为绑定给指令的对象
                    let width = '', height = ''
                    function isReize() {
                        const style = document.defaultView.getComputedStyle(el)
                        if (width !== style.width || height !== style.height) {
                            binding.value()
                        }
                        width = style.width;
                        height = style.height
                    }
                    el.__vueSetInterval__ = setInterval(isReize, 200)
                },
                unbind(el) {
                    clearInterval(el.__vueSetInterval__)
                }
            }
        },
        data () {
            return {
                hasInitData: false,
                atomPropsValue: {},
                atomPropsModel: {},
                containerInfo: {},
                atomDisabled: false,
                currentUserInfo: {}
            }
        },
        computed: {
            ...mapState([
                'extraHeight'
            ])
        },
        created () {
            window.addEventListener('message', (e) => {
                // if (location.href.indexOf(e.origin) === 0) return
                if (e.data && e.data.atomPropsValue && e.data.atomPropsModel) {
                    // 把用户的值和默认值合起来
                    const atomPropsValue = this.getAtomValue(e.data.atomPropsValue, e.data.atomPropsModel)
                    console.log("created -> atomPropsValue", atomPropsValue)
                    this.atomPropsValue = atomPropsValue
                    this.atomPropsModel = e.data.atomPropsModel
                    this.containerInfo = e.data.containerInfo
                    this.atomDisabled = e.data.atomDisabled
                    this.currentUserInfo = e.data.currentUserInfo
                    this.hasInitData = true
                }
            })
        },
        methods: {
            // 当iframe内容高度变化时，通知父级响应变化
            resize () {
                this.$nextTick(() => {
                    window.parent && window.parent.postMessage({ iframeHeight: document.body.scrollHeight + 10 }, '*')
                })
            },
            getAtomValue (atomValue = {}, atomProps = {}) {
                return Object.keys(atomProps).reduce((formProps, key) => {
                    formProps[key] = atomValue[key] || atomProps[key].default
                    return formProps
                }, {})
            }
        }
    }
</script>

<style scoped>
    .atom-disabled {
        pointer-events: none;
    }
</style>