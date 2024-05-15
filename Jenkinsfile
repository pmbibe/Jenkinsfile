def list_service=[]
def sites = ["DC", "DC2", "DR"]
def REGISTRY_DC = ''
def REGISTRY_DC2 = ''
def REGISTRY_DR = ''
def BASTION_DC = ''
def BASTION_DC2 = ''
def BASTION_DR = ''
def user = ''
def helm_path = ''
def sitesVariables = [:]

node("built-in") {
    stage("Set OKD TOKEN") {
        def set_okd_token = [:]
        sites.each { site ->
            set_okd_token[site] = {
                stage("Get OKD_TOKEN_$site") {      
                    sitesVariables[site] = [:]
                    if (site == "DC") {
                      sitesVariables[site]["REGISTRY"] = REGISTRY_DC  
                      sitesVariables[site]["BASTION"] = BASTION_DC 
                    } else if (site == "DC2") {
                      sitesVariables[site]["REGISTRY"] = REGISTRY_DC2 
                      sitesVariables[site]["BASTION"] = BASTION_DC2  
                    } else if (site == "DR") {
                      sitesVariables[site]["REGISTRY"] = REGISTRY_DR 
                      sitesVariables[site]["BASTION"] = BASTION_DR 
                    }
                    withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {
                      sitesVariables[site]["TOKEN"] = sh(script: "sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} 'oc whoami -t'", returnStdout: true).trim()
                    }                       
      
                }
            }
        } 
        
        parallel set_okd_token

    }  
    stage("Choose Service") {
        env.BPM = input message: 'Enter BPM here !!!', 
                               ok: 'OK', 
                               parameters: [
                                  text(
                                    defaultValue: 'CNT', 
                                    description: 'Enter BPM here !!!', 
                                    name: 'BPM', 
                                    trim: true
                                    )
                                ]
        env.SERVICE_NAME = input(
            message: 'CHOOSE SERVICE',
            ok: 'Release!',
            parameters: [
                choice(
                    name: 'SERVICE_NAME',
                    choices: list_service,
                    description: 'Choose one'
                )
            ]
        )
         
    }

    stage("Update variable") {
      def port_service = SERVICE_NAME.split("\\.")[-1]
      withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {
        service_path = sh(
                        script: """
                          sshpass -p BASTION_PASS ssh ${user}@${BASTION_DC} 'find ${helm_path} -maxdepth 1 -type d | grep -E ".${port_service}\$"'
                        """,
                        returnStdout: true
                        ).trim()
        server_env_path = "${service_path}/files/server.env"
        server_2_env_path = "${service_path}/files/server-2.env"

        env.UPDATE_VARIABLES = input message: 'Enter variables here !!!', 
                             ok: 'UPADTE', 
                             parameters: [
                                text(
                                  defaultValue: '', 
                                  description: 'Enter variables here !!!', 
                                  name: 'UPADTE_VARIABLES', 
                                  trim: true
                                  )
                              ]
        def update_variable = [:]
        sites.each { site ->
          update_variable[site] = {
              stage("Update Variable $site") {  
                if (site == "DC") {
                  sh """
                    sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "sed -i "\\\$a#${BPM}" ${server_2_env_path}"
                  """                                   
                } 
                sh """
                  sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "sed -i "\\\$a#${BPM}" ${server_env_path}"
                """                           
                List<String> update_variables_list = UPDATE_VARIABLES.split('\n')
                for (String variable : update_variables_list) {
                    variable_name = variable.split("=")[0]
                    if (site == "DC") {
                      sh """
                          sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "sed -i '/^${variable_name}=/ s/\$/ #---- Updated, for more details check ${BPM}/' ${server_2_env_path} && \
                                                                            sed -i '/^${variable_name}/ s/^/#/' ${server_2_env_path} && \
                                                                            sed -i '\\\$a${variable}' ${server_2_env_path}
                                                                            "
                        """                        
                    }
                    sh """
                        sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "sed -i '/^${variable_name}=/ s/\$/ #---- Updated, for more details check ${BPM}/' ${server_env_path} && \
                                                                          sed -i '/^${variable_name}/ s/^/#/' ${server_env_path} && \
                                                                          sed -i '\\\$a${variable}' ${server_env_path}
                                                                          "
                      """                      
                  }
              }
          }
      }
        parallel update_variable
    }
  }                     
   

    

  stage ("Build image") {
    build_image = build job: "Build Hydro/$SERVICE_NAME", 
        wait: true, 
        propagate: true
  }
  
  stage ("Push image to each site") { 
    def deployments = [:]
    sites.each { site ->
                    deployments[site] = {
                    stage("Push image to $site") {
                      build job: "Push Hydro/Scan_And_Push_$site", 
                              wait: true, 
                              propagate: true,
                              parameters: [
                                string(name: 'OKD_TOKEN', value: sitesVariables[site]["OKD_TOKEN"]), 
                                string(name: 'REGISTRY', value: sitesVariables[site]["REGISTRY"])
                              ]
                      }   
                    }
                }
    parallel deployments
  }





}
