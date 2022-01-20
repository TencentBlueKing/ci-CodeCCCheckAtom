<template>
    <div :class="{ &quot;codecc-accordion&quot;: true, &quot;showCheckbox&quot;: showCheckbox }">
        <header :active="isShow" @click="toggleContent" class="header var">
            <header class="var-header codecc-header" slot="header">
                <span class="title" v-if="!labelDesc">{{ label }}</span>
                <span class="title" v-else>
                    {{ label }}
                    <bk-popover placement="right">
                        <i style="display:block;" class="bk-icon icon-info-circle"></i>
                        <div slot="content" style="white-space: pre-wrap;">
                            <div v-html="labelDesc"></div>
                        </div>
                    </bk-popover>
                </span>
                <span v-if="desc" class="desc" v-bk-tooltips.top="desc">{{ desc }}</span>
                <span class="fold-open"> 
                    {{ isShow ? '收起' : '展开' }} 
                    <i class="bk-icon icon-angle-down" style="display:inline-block">
                    </i>
                </span>
            </header>
            <!-- <i class="bk-icon icon-angle-down" /> -->
            <slot name="header"></slot>
        </header>
        <transition name="slideLeft">
            <section v-if="condition">
                <section v-if="isShow" class="content">
                    <slot name="content"></slot>
                </section>
            </section>
            <section v-else>
                <section v-show="isShow" class="content">
                    <slot name="content"></slot>
                </section>
            </section>
        </transition>
    </div>
</template>

<script>
    export default {
        name: 'codecc-accordion',
        props: {
            afterToggle: Function,
            showContent: {
                type: Boolean,
                default: false
            },
            showCheckbox: {
                type: Boolean,
                default: false
            },
            isError: {
                type: Boolean,
                default: false
            },
            condition: {
                type: Boolean,
                default: false
            },
            label: {
                type: String,
                default: ''
            },
            labelDesc: {
                type: String,
                default: ''
            },
            desc: {
                type: String,
                default: ''
            }
        },
        data () {
            return {
                isShow: this.showContent
            }
        },
        watch: {
            showContent (val) {
                this.isShow = val
            }
        },
        methods: {
            toggleContent: function () {
                this.isShow = !this.isShow
                if (typeof this.afterToggle === 'function') {
                    this.afterToggle(this.$el, this.isShow)
                }
            }
        }
    }
</script>

<style lang="scss">
    .codecc-accordion {
        // border: 1px solid $borderColor;
        border-radius: 3px;
        margin: 5px 0;
        font-size: 12px;
        .header {
            font-size: 12px;
            display: flex;
            color: #63656e;
            background-color: #fff;
            padding: 10px 0px;
            align-items: center;
            cursor: pointer;

            .fold-open {
                font-size: 12px;
                font-weight: normal;
                color: #3a84ff;
            }

            .icon-angle-down {
                display: block;
                margin: 2px 12px 0 0;
                transition: all 0.3s ease;
            }
            &[active] {
                .icon-angle-down {
                    transform: rotate(-180deg)
                }
            }
        }
        .content {
            padding: 0px 0px;
        }

        &.showCheckbox {
            > .header {
                background-color: #fff;
                color: 63656e;
                .bk-icon {
                    display: none;
                }
                .var-header {
                    width: 100%;
                    align-items: center;
                    justify-content: space-between;
                    .title {
                        width: 120px;
                        font-weight: bold;
                        display: inline-block;
                        overflow: hidden;
                    }
                    .desc {
                        display: inline-block;
                        color: #999;
                        width: 340px;
                        overflow: hidden;
                        white-space: nowrap;
                        text-overflow: ellipsis;
                    }
                    .fold-open {
                        display: inline-block;
                        float: right;
                    }
                }
            }
        }
    }

</style>
