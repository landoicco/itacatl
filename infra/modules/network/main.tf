# Standard zones only: Local and Wavelength zones are opt-in and lack services such as RDS.
data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)
}

resource "aws_vpc" "this" {
  cidr_block = var.cidr_block

  # The RDS endpoint and the SSM agent are reached by DNS name.
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.name}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name}-igw" }
}

# The backend instance lives here. There is no NAT gateway (~$33/month), so anything that needs
# the internet sits in a public subnet, and its security group decides who reaches it.
resource "aws_subnet" "public" {
  count = var.az_count

  vpc_id            = aws_vpc.this.id
  availability_zone = local.azs[count.index]
  cidr_block        = cidrsubnet(var.cidr_block, 8, count.index)

  # No public IP by default: the backend's launch template asks for one explicitly, so nothing
  # else placed here gets one by accident.
  map_public_ip_on_launch = false

  tags = { Name = "${var.name}-public-${local.azs[count.index]}" }
}

# The database lives here, with no route to the internet in either direction.
resource "aws_subnet" "private" {
  count = var.az_count

  vpc_id            = aws_vpc.this.id
  availability_zone = local.azs[count.index]
  # Offset by 128, so public and private ranges stay apart however many zones are added.
  cidr_block = cidrsubnet(var.cidr_block, 8, 128 + count.index)

  map_public_ip_on_launch = false

  tags = { Name = "${var.name}-private-${local.azs[count.index]}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table_association" "public" {
  count = var.az_count

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Only the implicit local route. An empty list makes Terraform remove any route added outside it,
# so the private subnets cannot quietly gain a way out.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id
  route  = []

  tags = { Name = "${var.name}-private" }
}

resource "aws_route_table_association" "private" {
  count = var.az_count

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# The default security group allows all traffic between its members and all egress. Nothing uses
# it, so its rules are removed: a resource created without a security group gets no access.
resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id

  ingress = []
  egress  = []

  tags = { Name = "${var.name}-default-unused" }
}
