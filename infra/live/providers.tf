provider "aws" {
  region = "sa-east-1"

  default_tags {
    tags = {
      project = "liberdade"
    }
  }
}
