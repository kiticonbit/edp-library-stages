/* Copyright 2019 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.buildimage

class BuildImageApplication {
    Script script

    void run(context) {


        def resourseSettings = [:]
        context.codebase.imageBuildArgs = []
        def buildconfigName = "${context.codebase.name}-${context.gerrit.branch.replaceAll("[^\\p{L}\\p{Nd}]+", "-")}"
        context.codebase.imageBuildArgs.push("--name=${buildconfigName}")
        context.codebase.imageBuildArgs.push("--strategy=docker")
        context.codebase.imageBuildArgs.push("--binary=true")

        resourseSettings["cpuLimits"] = "1"
        resourseSettings["memLimits"] = "1Gi"
        resourseSettings["cpuRequests"]= "50m"
        resourseSettings["memRequests"] = "500Mi"

        def resultTag
        def targetTags = [context.codebase.buildVersion, "latest"]
        script.openshift.withCluster() {
            script.openshift.withProject() {
                if (!script.openshift.selector("buildconfig", "${buildconfigName}").exists())
                    script.dir("${context.workDir}") {
                        script.dir("${context.JsWorkDir}"){
                            script.openshift.newBuild(context.codebase.imageBuildArgs)
                            script.openshift.patch("bc/${buildconfigName}", '\'{\"spec\":{\"resources\":{\"limits\":{\"cpu\":' + resourseSettings.cpuLimits + ',\"memory\":' + "\"${resourseSettings.memLimits}\"" + '},\"requests\":{\"cpu\":' + "\"${resourseSettings.cpuRequests}\"" + ',\"memory\":' + "\"${resourseSettings.memRequests}\"" + '}}}}\'')
                        }
                    }

                script.dir(context.codebase.deployableModuleDir) {
                    script.sh "tar -cf ${context.codebase.name}.tar *"
                    def buildResult = script.openshift.selector("bc", "${buildconfigName}").startBuild(
                            "--from-archive=${context.codebase.name}.tar",
                            "--wait=true")
                    resultTag = buildResult.object().status.output.to.imageDigest
                }
                script.println("[JENKINS][DEBUG] Build config ${context.codebase.name} with result " +
                        "${buildconfigName}:${resultTag} has been completed")


                targetTags.each() { tagName ->
                    script.openshift.tag("${script.openshift.project()}/${buildconfigName}@${resultTag}",
                            "${script.openshift.project()}/${buildconfigName}:${tagName}")
                }
            }
        }
    }
}
