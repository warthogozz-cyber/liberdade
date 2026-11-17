terraform {
  required_version = "~> 1.13.2"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.70.0"
    }
  }

  # Partial backend: the bucket comes from backend.hcl (ignored by Git), see backend.hcl.example.
  backend "s3" {
    key          = "live/terraform.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}
