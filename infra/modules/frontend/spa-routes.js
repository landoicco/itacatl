// CloudFront Function on the default behavior's viewer requests. A path whose last segment has no
// extension is a route of the app, not a file of the build, so it gets index.html and React
// Router renders it. /api/* never reaches this function: it has its own behavior.
function handler(event) {
  var request = event.request;
  var lastSegment = request.uri.split("/").pop();

  if (lastSegment.indexOf(".") === -1) {
    request.uri = "/index.html";
  }

  return request;
}
