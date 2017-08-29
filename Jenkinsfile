#!groovy
timestamps {
    node('docker') {
        slackJobDescription = "job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        try {
            dockerRepo = "test-war-runner-${env.BUILD_TAG}"
            stage("Build") {
                checkout scm

                sh "docker build --pull --no-cache --rm -f ${dockerRepo} ."
                image_sha = sh(
                        returnStdout: true,
                        script: "docker inspect -f '{{ .Config.Image }}' ${dockerRepo}"
                ).trim()
                echo image_sha

                writeFile(file: "${dockerRepo}.docker-image-sha", text: "${image_sha}")
                fingerprint "${dockerRepo}.docker-image-sha"
            }

            dockerPusher = "push-${env.BUILD_TAG}"
            try {
                milestone 100
                stage("Docker Push") {
                    service = readProperties file: 'service.properties'

                    dockerPushRepo = "${service.dockerUser}/war-runner:${env.BRANCH_NAME}"
                    lock("docker-push-war-runner") {
                        milestone 101
                        sh "docker tag ${dockerRepo} ${dockerPushRepo}"
                        withCredentials(
                                [[$class          : 'UsernamePasswordMultiBinding',
                                  credentialsId   : 'jenkins-docker-credentials',
                                  passwordVariable: 'DOCKER_PASSWORD',
                                  usernameVariable: 'DOCKER_USERNAME']]
                        ) {
                            sh """docker run -e DOCKER_USERNAME -e DOCKER_PASSWORD \\
                                           -v /var/run/docker.sock:/var/run/docker.sock \\
                                           --rm --name ${dockerPusher} \\
                                           docker:\$(docker version --format '{{ .Server.Version }}') \\
                                           sh -e -c \\
                                'docker login -u \"\$DOCKER_USERNAME\" -p \"\$DOCKER_PASSWORD\" && \\
                                 docker push ${dockerPushRepo} && \\
                                 docker logout'"""
                        }
                    }
                }
            } finally {
                sh returnStatus: true, script: "docker kill ${dockerPusher}"
                sh returnStatus: true, script: "docker rm ${dockerPusher}"

                sh returnStatus: true, script: "docker rmi ${dockerRepo}"

                sh returnStatus: true, script: "docker rmi \$(docker images -qf 'dangling=true')"
            }
        } catch (InterruptedException e) {
            currentBuild.result = "ABORTED"
            slackSend color: 'warning', message: "ABORTED: ${slackJobDescription}"
            throw e
        } catch (e) {
            currentBuild.result = "FAILED"
            slackSend color: 'danger', message: "FAILED: ${slackJobDescription}"
            throw e
        }
    }
}
