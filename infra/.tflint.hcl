# Run from infra/ with `tflint --recursive`, which applies this file to every stack.
# The Terraform ruleset ships with TFLint, so it needs no `tflint --init` download.
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}
