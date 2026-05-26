pipeline {
    agent any

    parameters {
        string(
            name: 'REGISTRY',
            defaultValue: 'docker.io',
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

        IMAGE_REPO     = 'shazzar/ems-api-gateway'
        SERVICE_NAME   = 'api-gateway'
        CONTAINER_NAME = 'ems-api-gateway'
        SERVICE_PORT   = '8000'
    }

    options {
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 20, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.TAG = params.IMAGE_TAG ?: sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()

                    env.IMAGE = "${params.REGISTRY}/${env.IMAGE_REPO}:${env.TAG}"
                }
                echo "Service: ${env.SERVICE_NAME} | Tag: ${env.TAG} | Environment: ${params.ENVIRONMENT}"
            }
        }

        stage('Test') {
            when {
                expression { !params.SKIP_TESTS }
            }
            steps {
//                dir('api-gateway') {
                sh 'mvn test -B'
//                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Image') {
            steps {
//                dir('api-gateway') {
                sh "docker build -t ${env.IMAGE} ."
//                }
            }
        }

        stage('Push Image') {
            steps {
                sh """
                    echo "$DOCKER_CREDS_PSW" | docker login ${params.REGISTRY} \
                    -u "$DOCKER_CREDS_USR" --password-stdin
                """
                sh "docker push ${env.IMAGE}"
            }
            post {
                always {
                    sh "docker logout ${params.REGISTRY} || true"
                    sh "docker rmi ${env.IMAGE} 2>/dev/null || true"
                }
            }
        }
    }

    post {
        success {
            echo "Deployment of ${env.SERVICE_NAME}:${env.TAG} to ${params.ENVIRONMENT} succeeded."
        }
        failure {
            echo "Deployment of ${env.SERVICE_NAME}:${env.TAG} to ${params.ENVIRONMENT} failed."
        }
    }
}
