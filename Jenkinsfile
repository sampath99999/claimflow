// Builds and tests everything, then on main pushes images to Azure Container Registry
// and rolls them out to AKS. Credentials are Jenkins credentials, never in the repo.
pipeline {
    agent any

    environment {
        REGISTRY = 'claimflowacr.azurecr.io'
        IMAGE_TAG = "${env.GIT_COMMIT?.take(12) ?: 'dev'}"
    }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Backend: build and test') {
            steps {
                sh './mvnw -B verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Frontends') {
            parallel {
                stage('claims-app') {
                    steps {
                        dir('web/claims-app') {
                            sh 'npm ci && npm run lint && npm run build'
                        }
                    }
                }
                stage('admin-app') {
                    steps {
                        dir('web/admin-app') {
                            sh 'npm ci && npm run lint && npm run build'
                        }
                    }
                }
            }
        }

        stage('Images') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: 'acr-push', usernameVariable: 'ACR_USER', passwordVariable: 'ACR_PASSWORD')]) {
                    sh 'echo "$ACR_PASSWORD" | docker login "$REGISTRY" -u "$ACR_USER" --password-stdin'
                    script {
                        ['gateway', 'member-service', 'claims-service', 'audit-service'].each { svc ->
                            sh "docker build -f ${svc}/Dockerfile -t ${REGISTRY}/claimflow/${svc}:${IMAGE_TAG} ."
                            sh "docker push ${REGISTRY}/claimflow/${svc}:${IMAGE_TAG}"
                        }
                    }
                }
            }
        }

        stage('Deploy to AKS') {
            when { branch 'main' }
            steps {
                withCredentials([file(credentialsId: 'aks-kubeconfig', variable: 'KUBECONFIG')]) {
                    sh 'kubectl apply -k k8s/'
                    script {
                        ['gateway', 'member-service', 'claims-service', 'audit-service'].each { svc ->
                            sh "kubectl -n claimflow set image deployment/${svc} ${svc}=${REGISTRY}/claimflow/${svc}:${IMAGE_TAG}"
                            sh "kubectl -n claimflow rollout status deployment/${svc} --timeout=180s"
                        }
                    }
                }
            }
        }
    }
}
