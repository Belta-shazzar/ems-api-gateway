pipeline {
    agent any

    parameters {
        string(
            name: 'REGISTRY',
            defaultValue: 'docker.io',
            description: 'Docker registry'
        )

        string(
            name: 'IMAGE_TAG',
            defaultValue: '',
            description: 'Image tag'
        )

        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'staging', 'prod'],
            description: 'Deployment environment'
        )

        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip Maven tests'
        )
    }

    environment {
        DOCKER_CREDS = credentials('docker-registry-credentials')

        IMAGE_REPO = 'shazzar/ems-api-gateway'
        SERVICE_NAME = 'api-gateway'
    }

    options {
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
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

                echo "Building ${env.IMAGE}"
            }
        }

        stage('Build Jar') {
            steps {
                sh """
                    chmod +x mvnw
                    ./mvnw clean package \
                      ${params.SKIP_TESTS ? '-DskipTests' : ''} \
                      -Dspring.profiles.active=prod \
                      -B
                """
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
                sh "docker build -t ${env.IMAGE} ."
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
                    sh "docker rmi ${env.IMAGE} || true"
                }
            }
        }
    }

    post {
        success {
            echo "Deployment succeeded."
        }

        failure {
            echo "Deployment failed."
        }
    }
}