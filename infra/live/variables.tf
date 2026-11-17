variable "domain" {
  description = "Registered domain of the product (the environments are subdomains of it)."
  type        = string
  default     = "liberdadefamilia.com.br"
}

variable "alert_email" {
  description = "E-mail that receives the alarms and budget notices (set in terraform.tfvars, never committed)."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository (owner/name) allowed to assume the deploy role through OIDC."
  type        = string
  default     = "warthogozz-cyber/liberdade"
}

variable "monthly_budget_usd" {
  description = "Monthly cost budget in US dollars."
  type        = number
  default     = 60
}
