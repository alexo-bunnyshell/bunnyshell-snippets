pipeline {
    
    parameters {
        string(name: 'DeployedBy', defaultValue: 'dev', description: 'Who is deploying?')
        string(name: 'DeployEnvironmentFE', defaultValue: 'development', description: 'Frontend deploy environment')
        string(name: 'DeployEnvironmentBE', defaultValue: 'development', description: 'Backend deploy environment')
        string(name: 'appGithubRepoURL', defaultValue: 'https://github.com/alexo-bunnyshell/example-github-actions.git')
        string(name: 'githubRepoURL', defaultValue: 'https://github.com/alexo-bunnyshell/template-playwright.git')
        string(name: 'bnsProjectID', defaultValue: "yOjXGALr8g")
        string(name: 'bnsClusterID', defaultValue:"g6N2JRRNov" )
        
    }
    
    environment {
        BUNNYSHELL_TOKEN = "41:03631fb7a023dad5c469f25cbd155aba"
    }
    
    agent any
        // docker { image 'bunnyshell/cli' }
        // any
    // }
    stages {

        stage('Test Docker') {
            steps {
                script {
                    sh 'docker info'
                }
            }
        }
 
 
        stage('Status:') {
            steps {
                script { 
                    currentBuild.description = "Trigger: ${params.DeployedBy} | FE: ${params.DeployEnvironmentFE} | BE: ${params.DeployEnvironmentBE}" 
                }
                sh "echo '${currentBuild.description}'"
            }
        }
        
        stage('Checkout Repo') {
            steps {
                script {
                    echo 'Checking out the GitHub repository...'
                    sh 'pwd'
                    dir('bunnyshell') {
                        checkout([$class: 'GitSCM',
                            branches: [[name: '*/tmp-test-tf']],
                            userRemoteConfigs: [[url: "${params.githubRepoURL}"]]]
                            )
                        sh 'ls -alh'
                        sh 'pwd'
                    }
                }
            }
        }
        
        stage("Create env") {
            
            
            steps{
                
                sh 'pwd'
                sh 'ls -alh'

            
                string templateFile='.bunnyshell/templates/playwright/bunnyshell.yaml'
            
                dir('bunnyshell') {
                    sh "cat ${templateFile}"
                    
                    sh """
                        echo "running in"
                        pwd
                        ls -alh 
                        docker run --env BUNNYSHELL_TOKEN\
                            --volume \$(pwd):/root/app \
                            bunnyshell/cli \
                            environments create --name jenkins-${BUILD_NUMBER} \
                            --from-path /root/app/${templateFile} \
                            --k8s ${params.bnsClusterID} \
                            --project ${params.bnsProjectID} \
                            --deploy
                        """
                }
            }
        }
        
        
        stage('Delete env') {
            environment {
                bunnyshellFolder='/var/lib/jenkins/workspace/BunnyAutomation/bunnyshell'
                envs = sh(script: """
                    docker run --env BUNNYSHELL_TOKEN \
                    --volume ${bunnyshellFolder}:/root/app  \
                    bunnyshell/cli \
                    env list --search="jenkins-${BUILD_NUMBER}" -o stylish \
                    | grep jenkins-${BUILD_NUMBER} \
                    | cut -d"|" -f 1 
                    """, returnStdout: true).trim()
                
            }
            steps {
                script {
                    echo "ENV code for the bunny stack: ${envs}"
                    def deleteErrorCode = sh(script: """
                        timeout 120 docker run --env BUNNYSHELL_TOKEN \
                            --volume ${bunnyshellFolder}:/root/app  \
                            bunnyshell/cli \
                            env delete --id ${envs}
                            """, returnStatus: true)
                            
                    if (deleteErrorCode == 124) {
                        echo "Timeout during env delete. Checking delete was successful:"
                    
                        def envCount = sh(script: """
                            docker run --env BUNNYSHELL_TOKEN \
                                bunnyshell/cli \
                                env list --search="jenkins-${BUILD_NUMBER}" -o stylish \
                                | grep jenkins-${BUILD_NUMBER} \
                                | wc -l
                                """, returnStdout:true).trim() 
                        if (envCount!="0") {
                            echo "env count is $envCount"
                            error("Error: Env delete has failed")
                        }
                        echo "The env was scucessfully deleted. All good!"
                    }
                            
                        

                      
                  
                }
            }
        }
    }
}