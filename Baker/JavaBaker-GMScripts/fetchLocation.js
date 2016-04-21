var request = require("request");
var cheerio = require("cheerio");
var list = ["Summary", "Field Summary", "Constructor Summary", "Method Summary"];
var tags = ["b", "h1", "h2", "h3"];

var url = "http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#String(byte[])";
//var url = "http://docs.oracle.com/javase/6/docs/api/java/lang/String.html";

var newTable = "<table></table>";
var newHtml = '<div class="stackoverflowExamples"><h2><img id="stackOverflowLogo" src="http://files.quickmediasolutions.com/so-images/stackoverflow.svg"> Code Examples<hr></h2>' + newTable + '</div>';

var api = "";
getModifiedHTML(url, api);

function getModifiedHTML(url, api) {
    request(url, function(err, response, body) {
        if (!err) {
            $ = cheerio.load(body);
            for (var j = 0; j < tags.length; j++) {
                $(tags[j]).each(callback);
            }
        } else
            console.log(err);
    });
}

function callback() {
    var text = $(this).text();
    for (var i = 0; i < list.length; i++) {
        if (text === list[i]) {
            console.log(text);
            var table = $(this).closest('table').parent();
            var html = table.html();
            if (html === null) {
                html = $(this).parent().html();
                console.log("here");
            }
            console.log(html);
            break;
        }
    }
}