variable "name" {
  type        = string
  description = "Resource name prefix, used in Name tags."
}

variable "azs" {
  type        = list(string)
  description = "Availability zones for the subnets (two AZs)."
}