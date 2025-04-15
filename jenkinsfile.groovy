pipeline {
    agent any


    environment {
        GCLOUD_AUTH = credentials('GCP-jenkins-json')           
        GCP_PROJECT = 'consummate-rig-453502-q2'
        GKE_CLUSTER = 'argocd-test'
        GKE_REGION = 'us-central1'
    }

    stages {
        stage('Authenticate GCP') {
            steps {
                withCredentials([file(credentialsId: 'GCP-jenkins-json', variable: 'SA_KEY')]) {
                sh '''
                gcloud auth activate-service-account --key-file=$SA_KEY
                gcloud config set project $GCP_PROJECT
                gcloud container clusters get-credentials $GKE_CLUSTER --region $GKE_REGION
                '''
            }
        }
    }   
        
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/GitCosmicray/APP-HELM'
            }
        }

        stage('Deploy Backend') {
            steps {
                sh '''
                helm upgrade --install backend ./backend-helmm \
                    -n backend --create-namespace
                '''
            }
        }

        stage('Deploy Frontend') {
            steps {
                sh '''
                helm upgrade --install frontend ./frontend-helmm \
                    -n frontend --create-namespace
                '''
            }
        }

        stage('Deploy Monitoring') {
            steps {
                sh '''
                helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
                helm repo add grafana https://grafana.github.io/helm-charts
                helm repo update

                helm upgrade --install prometheus ./monitoring_helm/prometheus \
                    -n monitoring --create-namespace

                helm upgrade --install grafana ./monitoring_helm/grafana \
                    -n monitoring --create-namespace
                '''
            }
        }

        stage('Sync with Argo CD') {
            steps {
                sh '''
                # Assuming Argo CD CLI is configured in Jenkins Agent
                # and your app is already registered in Argo CD
                argocd app sync backend-app
                argocd app sync frontend-app
                argocd app sync monitoring-app
                '''
            }
        }
    }

    post {
        success {
            echo '✅ Deployment completed successfully!'
        }
        failure {
            echo '❌ Deployment failed!'
        }
    }
}
