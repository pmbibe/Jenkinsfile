def list_service=['']
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
def get_healthcheck_replicas = "oc get deploy/ducptm-healthcheck -n health-gateway -o=jsonpath='{.spec.replicas}'"
def get_edge_replicas = "oc get deploy/edge -n pmbibe-ducptm -o=jsonpath='{.spec.replicas}'"
def turn_off_edge = { deployment_ID ->
    deployment_ID == 1 ? "oc scale deploy/edge --replicas=0 -n pmbibe-ducptm" : "oc scale deploy/edge-${deployment_ID.toString()} --replicas=0 -n pmbibe-ducptm"
}

def turn_on_edge = { deployment_ID ->
    deployment_ID == 1 ? "oc scale deploy/edge --replicas=1 -n pmbibe-ducptm" : "oc scale deploy/edge-${deployment_ID.toString()} --replicas=1 -n pmbibe-ducptm"
}
def helm_upgrade_command = { deployment_ID, service_path ->
                              folder_name = service_path.split("/")[-1]
                              def deployment_name = folder_name.split("\\.")[1]
                                if (deployment_name == "noti") {
                                  deployment_ID == 1 ? "helm upgrade noti-presentation ${service_path} -f ${service_path}/values.yaml -n pmbibe-ducptm" : "helm upgrade noti-presentation-${deployment_ID.toString()} ${service_path} -f ${service_path}/values.yaml -n pmbibe-ducptm"
                                } else {
                                  deployment_ID == 1 ? "helm upgrade ${deployment_name} ${service_path} -f ${service_path}/values.yaml -n pmbibe-ducptm" : "helm upgrade ${deployment_name}-${deployment_ID.toString()} ${service_path} -f ${service_path}/values.yaml -n pmbibe-ducptm"
                                }
                          }
def turn_off_healthcheck = "oc scale deploy/ducptm-healthcheck --replicas=0 -n health-gateway"
def turn_on_healthcheck = "oc scale deploy/ducptm-healthcheck --replicas=1 -n health-gateway"

node("built-in") {

    stage("GET OKD INFORMATION") {
        def set_okd_token = [:]
        sites.each { site ->
            set_okd_token[site] = {
                stage("Get $site Information") {      

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
                      sitesVariables[site]["TOKEN"] = sh(
                          script: """
                            sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} 'oc whoami -t'
                          """,
                          returnStdout: true
                          ).trim()
                      sitesVariables[site]["HEATHCHECK_RUNNING"] = sh(
                          script: """
                            sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "${get_healthcheck_replicas}"
                            """, 
                          returnStdout: true
                          ).trim()

                    }                       
      
                }
            }
        } 
        
        parallel set_okd_token

    }  
    stage("Choose Service") {
        env.BPM_AND_PORT = input message: 'Enter BPM and Port of Service here !!!', 
                                ok: 'Enter', 
                                parameters: [
                                  string(
                                    defaultValue: 'CNT', 
                                    description: 'BPM', 
                                    name: 'BPM', 
                                    trim: true), 
                                  string(
                                    description: 'PORT OF SERVICE', 
                                    name: 'PORT', 
                                    trim: true)
                                    ]

        BPM_AND_PORT = BPM_AND_PORT.replaceAll("[{}]", "").split(", ").collectEntries { entry ->
            def (KEY, VALUE) = entry.split("=")
            [(KEY): VALUE]
        }

        env.SERVICE_NAME = list_service.find { it.contains(BPM_AND_PORT["PORT"]) }
        env.BPM = BPM_AND_PORT["BPM"]

        if (SERVICE_NAME == "null") {  
          error("Service not found. Try again later")
        }      

         
    }

    stage ("Build image") {
      build_image = build job: "Build ducptm/$SERVICE_NAME", 
          wait: true, 
          propagate: true
    }
    
    stage ("Push image to each site") { 
      def deployments = [:]
      sites.each { site ->
                      deployments[site] = {
                      stage("Push image to $site") {
                        build job: "Push ducptm/Scan_And_Push_$site", 
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

    stage("Enter variables") {
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
    }
    stage("Update variable") {
      def port_service = SERVICE_NAME.split("\\.")[-1]
      withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {
        def update_variable = [:]
        sites.each { site ->
          update_variable[site] = {
              stage("Update Variable $site") {  
                if (port_service == "9302") {
                  sitesVariables[site]["SERVICE_PATH"] = sh(
                                  script: """
                                    sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} 'find ${helm_path} -maxdepth 1 -type d | grep "dbs_ha_1971.noti-internal.9302"'
                                  """,
                                  returnStdout: true
                                  ).trim()                  
                } else
                {
                  sitesVariables[site]["SERVICE_PATH"] = sh(
                                  script: """
                                    sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} 'find ${helm_path} -maxdepth 1 -type d | grep -E ".${port_service}\$"'
                                  """,
                                  returnStdout: true
                                  ).trim()
                }

                server_env = sh(
                  script: """
                    sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} 'find ${sitesVariables[site]["SERVICE_PATH"]}/files/ -type f -name "server*.env"'
                  """,
                  returnStdout: true
                ).trim()
                sitesVariables[site]["SERVER_ENV"] = server_env.split("\n")
                sitesVariables[site]["NUMBER_OF_DEPLOYMENT"] = sitesVariables[site]["SERVER_ENV"].size()
                for (server_env_file in sitesVariables[site]["SERVER_ENV"]) {
                  sh """
                    sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "sed -i "\\\$a#${BPM}" ${server_env_file}"
                  """   
                  
                }                           
                List<String> update_variables_list = UPDATE_VARIABLES.split('\n')
                for (String variable : update_variables_list) {
                    variable_name = variable.split("=")[0]
                      for (server_env_file in sitesVariables[site]["SERVER_ENV"]) {
                        sh """
                            sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "sed -i '/^${variable_name}=/ s/\$/ #---- Updated, for more details check ${BPM}/' ${server_env_file} && \
                                                                              sed -i '/^${variable_name}/ s/^/#/' ${server_env_file} && \
                                                                              sed -i '\\\$a${variable}' ${server_env_file}
                                                                              "
                          """
                      }                                                           
                    }                  
                  }
              }
          }
        parallel update_variable
      }
    }


    stage ("Deploy ?") {
        
        env.userInput = input(
                        id: 'userInput', 
                        message: 'Do you want to deploy?', 
                        parameters: [choice(choices: 'Now\nLater', description: 'Select Now or Later', name: 'Now or Later')]
                    )
    }                 
        if (userInput == 'Now') {
            def helm_upgrade = [:]
            sites.each { site ->
                            helm_upgrade[site] = {
                              if (sitesVariables[site]["HEATHCHECK_RUNNING"] != "0") {
                                stage("Turn Off Healthcheck $site") {
                                  if (sitesVariables[site]["HEATHCHECK_RUNNING"] != "0") {
                                    sh(
                                        script: """
                                          sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "${turn_off_healthcheck}"
                                          """
                                    )
                                    sitesVariables[site]["TURNOFF_HC"] = 1
                                    
                                  }
                                }
                              }
                              if (sitesVariables[site]["TURNOFF_HC"] == 1){
                                stage("Deploy to $site") {
                                  def deployment_list = [:]
                                  def deployment_EID = (1..sitesVariables[site]["NUMBER_OF_DEPLOYMENT"]).toList()
                                    deployment_EID.each { deployment_eid ->
                                      deployment_list[deployment_eid] = {
                                        stage("Turn off Edge of Deployment ${deployment_eid}"){
                                          sitesVariables[site]["IS_STOP_EDGE_${deployment_eid}"] = input(
                                                          id: "stop_edge_${deployment_eid}", 
                                                          message: "Do you want to stop edge ${deployment_eid} ? ", 
                                                          parameters: [choice(choices: 'Now\nLater', description: "Do you want to stop edge ${deployment_eid} ? ", name: 'Now or Later')]
                                                      )
                                          if (sitesVariables[site]["IS_STOP_EDGE_${deployment_eid}"] == 'Now') {
                                            println turn_off_edge(deployment_eid)

                                          } else {
                                              echo 'Job cancelled by user.'
                                              currentBuild.result = 'ABORTED'
                                              error("Job aborted by user.")
                                            }
                                          }
                                            stage("Deployment ${deployment_eid} - Helm Upgrade and Rollout") {
                                                println helm_upgrade_command(deployment_eid,sitesVariables[site]["SERVICE_PATH"])
                                              }
                                            stage("Turn on Edge of Deployment ${deployment_eid}"){
                                                sitesVariables[site]["IS_START_EDGE_${deployment_eid}"] = input(
                                                                  id: "start_edge_${deployment_eid}", 
                                                                  message: "Do you want to start edge ${deployment_eid} ? ", 
                                                                  parameters: [choice(choices: 'Now\nLater', description: "Do you want to start edge ${deployment_eid} ? ", name: 'Now or Later')]
                                                              )
                                                if (sitesVariables[site]["IS_START_EDGE_${deployment_eid}"] == 'Now') {
                                                    println turn_on_edge(deployment_eid)
                                                } else {
                                                      echo 'Job cancelled by user.'
                                                      currentBuild.result = 'ABORTED'
                                                      error("Job aborted by user.")
                                                }   
                                              }                                        
                                        }
                                      } 
                                        parallel deployment_list                                       
                                  } 
                                stage("Turn On Healthcheck $site") {  
                                  is_turn_on_healthcheck = input(
                                                  id: 'is_turn_on_healthcheck', 
                                                  message: "Do you want to turn on Healthcheck on $site?", 
                                                  parameters: [choice(choices: 'Now\nLater', description: 'Select Now or Later', name: 'Now or Later')]
                                              ) 
                                  if (is_turn_on_healthcheck == 'Now') {
                                    sh(
                                        script: """
                                          sshpass -p BASTION_PASS ssh ${user}@${sitesVariables[site]["BASTION"]} "${turn_on_healthcheck}"
                                          """
                                    )                                    
                                  } else {
                                      echo 'Job cancelled by user.'
                                      currentBuild.result = 'ABORTED'
                                      error("Job aborted by user.")
                                    }                                                                                                                           
                                  }

                              }
            
                            }
          
            }
            parallel helm_upgrade
         


 
  } else {
            echo 'Job cancelled by user.'
            currentBuild.result = 'ABORTED'
            error("Job aborted by user.")
        }
}
