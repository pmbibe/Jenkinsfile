def list_service=[]
def sites = ["DC", "DC2", "DR"]
def REGISTRY_DC = ''
def REGISTRY_DC2 = ''
def REGISTRY_DR = ''
def sitesVariables = [:]

node("built-in") {
    stage("Set OKD TOKEN") {
        def set_okd_token = [:]
        sites.each { site ->
            set_okd_token[site] = {
                stage("Set OKD_TOKEN_$site") {      
                    def OKD_TOKEN = input(
                        id: "OKD_TOKEN_$site",
                        message: "Enter OKD_TOKEN for $site:",
                        parameters: [
                            string(
                                name: "OKD_TOKEN_$site", 
                                defaultValue: '', 
                                description: "Enter your OKD_TOKEN for $site"
                            )
                        ]
                    )
                    sitesVariables[site] = [:]
                    sitesVariables[site]["OKD_TOKEN"] = OKD_TOKEN  
                    if (site == "DC") {
                      sitesVariables[site]["REGISTRY"] = REGISTRY_DC  
                    } else if (site == "DC2") {
                      sitesVariables[site]["REGISTRY"] = REGISTRY_DC2  
                    } else if (site == "DR") {
                      sitesVariables[site]["REGISTRY"] = REGISTRY_DR 
                    }   
      
                }
            }
        } 
        
        parallel set_okd_token

    }  
    stage("Choose Service") {
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

  stage ("Build image") {
    build_image = build job: "Build Ducptm/$SERVICE_NAME", 
        wait: true, 
        propagate: true
  }
  
  stage ("Push image to each site") { 
    def deployments = [:]
    sites.each { site ->
                    deployments[site] = {
                    stage("Push image to $site") {
                      build job: "Push Ducptm/Scan_And_Push_$site", 
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


