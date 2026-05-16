pipeline {
    agent any

    parameters {
        string(
            name: 'REGISTRY',
            defaultValue: 'docker.io/shazzar',
            description: 'Docker registry (e.g. docker.io/your-org or ghcr.io/your-org)'
        )
        string(
            name: 'IMAGE_TAG',
            defaultValue: '',
            description: 'Image tag — defaults to the short Git commit SHA'
        )
        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'staging', 'prod'],
            description: 'Target deployment environment'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip Maven tests'
        )
    }

    environment {
        DOCKER_CREDS   = credentials('docker-registry-credentials')
//         DEPLOY_USER    = credentials('deploy-user')
//         DEPLOY_HOST    = credentials('deploy-host')

        SERVICE_NAME   = 'api-gateway'
        CONTAINER_NAME = 'ems-api-gateway'
        SERVICE_PORT   = '8000'
        TAG            = "${params.IMAGE_TAG ?: sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()}"
        IMAGE          = "${params.REGISTRY}/ems-api-gateway:${TAG}"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 20, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                echo "Service: ${env.SERVICE_NAME} | Tag: ${env.TAG} | Environment: ${params.ENVIRONMENT}"
            }
        }

        stage('Test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
                dir('api-gateway') {
                    sh 'mvn test -B'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'api-gateway/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Image') {
            steps {
                dir('api-gateway') {
                    sh "docker build -t ${env.IMAGE} ."
                }
            }
        }

        stage('Push Image') {
            steps {
                sh "echo ${DOCKER_CREDS_PSW} | docker login ${params.REGISTRY} -u ${DOCKER_CREDS_USR} --password-stdin"
                sh "docker push ${env.IMAGE}"
            }
            post {
                always {
                    sh "docker logout ${params.REGISTRY} || true"
                }
            }
        }

//         stage('Deploy') {
//             steps {
//                 sshagent(['deploy-ssh-key']) {
//                     sh """
//                         ssh -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} '
//                             docker pull ${env.IMAGE}
//                             docker stop ${env.CONTAINER_NAME} 2>/dev/null || true
//                             docker rm   ${env.CONTAINER_NAME} 2>/dev/null || true
//                             docker run -d \\
//                                 --name ${env.CONTAINER_NAME} \\
//                                 --network ems-network \\
//                                 --restart unless-stopped \\
//                                 -p ${env.SERVICE_PORT}:${env.SERVICE_PORT} \\
//                                 -e SPRING_PROFILES_ACTIVE=${params.ENVIRONMENT} \\
//                                 -e CONFIG_SERVER_HOST=ems-config-server \\
//                                 ${env.IMAGE}
//                         '
//                     """
//                 }
//             }
//         }
//
//         stage('Health Check') {
//             steps {
//                 retry(5) {
//                     sleep time: 15, unit: 'SECONDS'
//                     sh "curl -sf http://${DEPLOY_HOST}:${env.SERVICE_PORT}/actuator/health | grep -q UP"
//                 }
//             }
//         }
    }

    post {
        success {
            echo "Deployment of ${env.SERVICE_NAME}:${env.TAG} to ${params.ENVIRONMENT} succeeded."
        }
        failure {
            echo "Deployment of ${env.SERVICE_NAME}:${env.TAG} to ${params.ENVIRONMENT} failed."
        }
        always {
            sh "docker rmi ${env.IMAGE} 2>/dev/null || true"
        }
    }
}
