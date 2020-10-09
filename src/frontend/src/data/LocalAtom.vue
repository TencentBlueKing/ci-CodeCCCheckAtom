<template>
    <div class="remote-atom" id="local-atom" :class="{ 'atom-disabled': atomDisabled}">
        <Atom
            :atom-props-value="getAtomDefaultValue(taskJson && taskJson.input)"
            :atom-props-model="taskJson && taskJson.input"
            :atom-props-container-info="containerInfo"
            :atom-props-disabled="atomDisabled"
            :current-user-info="currentUserInfo">
        </Atom>
    </div>
</template>

<script>
    import initTaskJson from './task.json'
    import Atom from '../Atom'
    import { mapState } from 'vuex'
    export default {
        name: 'local-atom',
        components: {
            Atom
        },
        data () {
            return {
                containerInfo: {
                    baseOS: 'WINDOWS',
                    dispatchType: {
                        buildType: "THIRD_PARTY_AGENT_ID",
                        imageCode: "tlinux2_2",
                        imageName: "TLinux2.2公共镜像",
                        imageType: "BKSTORE",
                        imageVersion: "1.*",
                        value: "dev.artifactory.tencent.com:8090/paas/bkdevops/docker-builder2.2:v1"
                    }
                },
                atomDisabled: false,
                currentUserInfo: {}
            }
        },
        computed: {
            taskJson () {
                return initTaskJson
            },
            ...mapState([
                'extraHeight'
            ])
        },
        methods: {
            getAtomDefaultValue (atomProps = {}) {
                return Object.keys(atomProps).reduce((formProps, key) => {
                    formProps[key] = atomProps[key].default
                    return formProps
                }, {})
            }
        }
    }
</script>

<style lang="css">
    #local-atom {
        width: 630px;
        margin: 30px auto;
        border: solid 1px #c4c6cc;
        padding: 32px;
    }
    .atom-disabled {
        pointer-events: none;
    }
</style>