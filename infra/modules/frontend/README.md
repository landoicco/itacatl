# frontend

The React build in a private S3 bucket and the CloudFront distribution in front of the whole marketplace: the app and `/api` on one origin, so the browser needs no CORS and the session cookie stays first-party ([architecture](../../../docs/infra-plan.md#architecture)).

- **One distribution, two origins.** The default behavior serves the bucket; `/api/*` goes to the backend's Elastic IP, never cached, with every method and the viewer's cookies, headers and query strings (all but `Host`, which must name the origin). Locally, the frontend's nginx plays the same role.
- **The bucket is private.** Public access is blocked and ACLs are off; CloudFront reads it through origin access control, and the bucket policy admits only this distribution, over TLS. It is named `<name>-frontend-<account ID>`, like the state bucket, so the name is unique without the account ID in the repository.
- **The app's routes are not files.** A CloudFront Function on the default behavior gives `index.html` to every path without an extension (`/shops/...`, `/manage/...`), and React Router renders it. A custom error response would be simpler, but it applies to every origin and would replace the API's own 404s. Its two million free invocations a month are far above a small marketplace's traffic.
- **Caching:** the build's files carry a content hash in their names, so they are cached with AWS's `CachingOptimized` policy; `index.html` is uploaded with `Cache-Control: no-cache`, so a deploy shows at once ([runbook](../../../docs/terraform.md#deploying-the-frontend)).
- **Security headers** on every response, from AWS's managed `SecurityHeadersPolicy`: HSTS, `nosniff`, frame options, referrer policy.
- **The domain is optional.** It depends on which business launches the marketplace, so it is a deployment setting, not a decision of the code. Without one, the marketplace is served on CloudFront's own `*.cloudfront.net` domain and certificate. With one (`domain = { name, zone_id }`), the module adds an ACM certificate validated through DNS, TLS 1.2 or later, and A and AAAA alias records in the given Route 53 hosted zone, which exists outside this repository. CloudFront only takes certificates from `us-east-1`, so a domain in another region fails at plan time.
- **Cheapest price class** (`PriceClass_100`): North America, Mexico included, and Europe.
- **Left out because they bill monthly:** access logs, WAF (rate limiting is an [open decision](../../../docs/infra-plan.md#open-decisions)), and a customer-managed key. Versioning is off: every file can be rebuilt from its commit, which is also why a destroy empties the bucket.

```hcl
module "frontend" {
  source                     = "../../modules/frontend"
  name                       = "itacatl-prod"
  backend_origin_domain_name = module.backend.origin_domain_name
  backend_origin_port        = module.backend.origin_port
  domain                     = { name = "shop.example.com", zone_id = "Z0123456789ABCDEFGHIJ" } # or null
}
```

## Known limitations

- **CloudFront reaches the backend over plain HTTP.** The instance has no certificate for its address, so the hop from CloudFront's edge to the instance is not encrypted; the viewer's side always is. The backend admits only CloudFront's addresses, but any distribution's, not only this one's. An [open decision](../../../docs/infra-plan.md#open-decisions), to settle before launch.
- **A missing file is a 403 from S3, not the app's 404 page.** A path with an extension that is not in the build (`/favicon.ico`, or a file the last deploy removed) reaches the bucket, and without `s3:ListBucket` S3 answers `AccessDenied` in XML rather than `NoSuchKey`. A custom error response could turn it into the app's page, but it would also replace the API's own 403s (`ACCESS_DENIED`); paths without an extension, the app's routes, always get the app.

## Accepted Trivy findings

Each is ignored inline, next to the resource, with its reason:

- `AVD-AWS-0089`, S3 access logging: it needs a second bucket; CloudFront is the bucket's only reader.
- `AVD-AWS-0090`, S3 versioning: it would keep copies of files that every commit rebuilds.
- `AVD-AWS-0132`, S3 without a customer-managed key: such a key bills monthly.
- `AVD-AWS-0010`, CloudFront access logs: they bill for their storage; decided with the observability module.
- `AVD-AWS-0011`, CloudFront without WAF: WAF bills monthly; rate limiting is an open decision.

## First apply

A mocked provider cannot check these against AWS; confirm them on the first apply:

- AWS's managed policies are found by their names: `Managed-CachingOptimized`, `Managed-CachingDisabled`, `Managed-AllViewerExceptHostHeader`, `Managed-SecurityHeadersPolicy`.
- The distribution deploys (it takes several minutes) and `https://<distribution domain>/` serves the app; a route such as `/manage` loads too, and `/api/businesses` answers from the backend.
- Signing in works through CloudFront: the session cookie and the `XSRF-TOKEN` cookie reach the browser, and a POST with its header is accepted.
- With a domain: the certificate validates within a few minutes, and the domain resolves to the distribution.

<!-- BEGIN_TF_DOCS -->
## Requirements

| Name | Version |
| ---- | ------- |
| terraform | ~> 1.17 |
| aws | >= 6.73 |

## Providers

| Name | Version |
| ---- | ------- |
| aws | >= 6.73 |

## Modules

No modules.

## Resources

| Name | Type |
| ---- | ---- |
| [aws_acm_certificate.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/acm_certificate) | resource |
| [aws_acm_certificate_validation.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/acm_certificate_validation) | resource |
| [aws_cloudfront_distribution.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudfront_distribution) | resource |
| [aws_cloudfront_function.spa_routes](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudfront_function) | resource |
| [aws_cloudfront_origin_access_control.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudfront_origin_access_control) | resource |
| [aws_route53_record.alias](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route53_record) | resource |
| [aws_route53_record.certificate_validation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/route53_record) | resource |
| [aws_s3_bucket.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket) | resource |
| [aws_s3_bucket_ownership_controls.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_ownership_controls) | resource |
| [aws_s3_bucket_policy.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_policy) | resource |
| [aws_s3_bucket_public_access_block.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_public_access_block) | resource |
| [aws_s3_bucket_server_side_encryption_configuration.this](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_server_side_encryption_configuration) | resource |
| [aws_caller_identity.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/caller_identity) | data source |
| [aws_cloudfront_cache_policy.disabled](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/cloudfront_cache_policy) | data source |
| [aws_cloudfront_cache_policy.optimized](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/cloudfront_cache_policy) | data source |
| [aws_cloudfront_origin_request_policy.all_viewer_except_host](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/cloudfront_origin_request_policy) | data source |
| [aws_cloudfront_response_headers_policy.security_headers](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/cloudfront_response_headers_policy) | data source |
| [aws_region.current](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/region) | data source |

## Inputs

| Name | Description | Type | Default | Required |
| ---- | ----------- | ---- | ------- | :------: |
| backend\_origin\_domain\_name | DNS name the backend answers on, which CloudFront forwards /api/* to. | `string` | n/a | yes |
| backend\_origin\_port | Port the backend answers on over HTTP; the backend module's origin\_port output. | `number` | n/a | yes |
| name | Prefix of every resource name, such as itacatl-prod. | `string` | n/a | yes |
| domain | The marketplace's own domain and its Route 53 hosted zone. Null serves it on CloudFront's *.cloudfront.net domain, with no certificate or DNS records; the domain is a deployment setting, chosen by whoever launches. | ```object({ name = string zone_id = string })``` | `null` | no |

## Outputs

| Name | Description |
| ---- | ----------- |
| api\_origin\_domain\_name | Address CloudFront forwards /api/* to. |
| api\_origin\_port | Port CloudFront forwards /api/* to, over HTTP. |
| bucket\_name | S3 bucket that deploys upload the frontend's build to. |
| distribution\_domain\_name | CloudFront's own domain for the distribution (*.cloudfront.net). |
| distribution\_id | ID of the CloudFront distribution, which a deploy invalidates index.html on. |
| url | Address the marketplace is served on: the domain when one is given, CloudFront's otherwise. |
<!-- END_TF_DOCS -->
