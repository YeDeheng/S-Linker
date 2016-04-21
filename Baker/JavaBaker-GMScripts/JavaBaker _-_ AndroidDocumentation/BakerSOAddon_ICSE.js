// ==UserScript==
// @name        BakerSOAddOn
// @namespace   JavaBaker
// @version     1
// @include		http://stackoverflow.com/*
// @include		stackoverflow.com/*
// @include		https://stackoverflow.com/*
// @require		http://code.jquery.com/jquery-1.9.1.js
// @require		http://code.jquery.com/ui/1.10.3/jquery-ui.js
// @resource	customCSS	http://code.jquery.com/ui/1.10.4/themes/smoothness/jquery-ui.css
// ==/UserScript==


function getAnswerId()
{
    var aid = $(".answer.accepted-answer").attr("data-answerid");
    return aid;
}

function getCodeCss()
{
    alert("here");
    var css = $(".answer.accepted-answer").find(".post-text").find("code").css();
    alert("here");
    return css;
}

function getCodeBlockText()
{
    var code = $(".answer.accepted-answer").find(".post-text").find("code").first().html();
    return code;
}

function getUrlClass(element)
{
    var base = androidBaseUrl;
    var arr = element.split(".");
    for(var i = 0; i<arr.length; i++)
    {
        var token = arr[i];
        token = token.replace('$', '.');
        base = base + token + '/';
    }
    base = base.substring(0, base.length - 1) + ".html";
    return base;
}

function getUrlMethod(element)
{
    var base = androidBaseUrl;
    var arr = element.split(".");
    for(var i = 0; i<arr.length-1; i++)
    {
        var token = arr[i];
        token = token.replace('$', '.');
        base = base + token + '/';
    }
    base = base.substring(0, base.length - 1) + ".html";
    base = base + '#' + arr[arr.length-1];
    return base;
}

function addUL(ip)
{
 	ip = ip.replace(/mChronometer/g, '<u class=\"code\">mChronometer</u>');   
    ip = ip.replace(/mStartButtonListener/g, '<u class=\"code\">mStartButtonListener</u>');   
    ip = ip.replace(/onClick/g, '<u class=\"code\">onClick</u>');
    ip = ip.replace(/setBase/g, '<u class=\"code\">setBase</u>');
    ip = ip.replace(/elapsedRealtime/g, '<u class=\"code\">elapsedRealtime</u>');
    ip = ip.replace(/start/g, '<u class=\"code\">start</u>');
    ip = ip.replace(/View /g, '<u class=\"code\">View</u>');
    ip = ip.replace(/View.OnClickListener/g, '<u class=\"code\">View.OnClickListener</u>');
    ip = ip.replace(/SystemClock/g, '<u class=\"code\">SystemClock</u>');
    
    ip = ip.replace(/View.OnClickListener/g, '<font color="#2b91af">View.OnClickListener</font>');
    ip = ip.replace(/new/g, '<font color="#00008b">new</font>');
    ip = ip.replace(/@Override/g, '<font color="#800000">@Override</font>');
    ip = ip.replace(/public void/g, '<font color="#00008b">public void</font>');
    ip = ip.replace(/View/g, '<font color="#2b91af">View</font>');
    ip = ip.replace(/SystemClock/g, '<font color="#2b91af">SystemClock</font>');
    return ip;
}

function getUrlTitle(url)
{
    /*
    var answer = "";
    GM_xmlhttpRequest({
        url: url,
        method : "GET",
        synchronous	: true,
        onload: function(data) {
            answer = data.responseText;
            var matches = answer.match(/<title>(.*?)<\/title>/);
            answer = matches[0];
            answer = answer.substring(7, answer.length-8);
        }
    });
    alert(answer);
    return answer;
    */
}

function updatePage(response)
{
    var map2 = {},
        json = JSON.parse(response.responseText),
        cutype = 0;
    for(var i=0; i<json.length; i++)
    {
        var row = json[i];
        if(map2.hasOwnProperty(row.line))
        {
            if(row.apitype == "class")
            {
                map2[row.line].class.push(row.element);
            }
            else
            {
                map2[row.line].method.push(row.element);
            }
        }
        else
        {
            cutype = row.cutype;
            map2[row.line] = {};
            map2[row.line].method = [];
            map2[row.line].class = [];
            if(row.apitype == "class")
            {
                map2[row.line].class.push(row.element);
            }
            else
            {
                map2[row.line].method.push(row.element);
            }
        }
    }
    
    var tableMap = {};
    var count = cutype + 1;
    var arr = code.split('\n');
    var newCode = "";
    for(var i=0; i < arr.length; i++)
    {
        var lineno = i+1;
        var titleString = "<table align=\"left\">";
        if(map2.hasOwnProperty(i+count))
        {
            var values = map2[i+count]["class"];
            if(values.length >0)
            {
                titleString = titleString + "<tr><td align='left'>Classes: </td> <td>&nbsp;&nbsp;&nbsp;&nbsp;</td> <td></td></tr>"
            }
            for(var j=0; j<values.length; j++)
            {
                var urlString = getUrlMethod(values[j]);
                titleString = titleString + "<tr><td align='left'>" + values[j] + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td><td align='left'><a href=\"" + urlString +"\">" + getUrlTitle(urlString) + "</a></td></tr>";
            }
            var values = map2[i+count]["method"];
            if(values.length >0)
            {
                titleString = titleString + "<tr><td>&nbsp;&nbsp;&nbsp;&nbsp;</td></tr><tr><td>&nbsp;&nbsp;&nbsp;&nbsp;</td></tr><tr><td align='left'>Methods: </td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td><td></td></tr>"
            }
            for(var j=0; j<values.length; j++)
            {
                var urlString = getUrlMethod(values[j]);
                titleString = titleString + "<tr><td align='left'>" + values[j] + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td><td align='left'><a href=\"" + urlString +"\">" + getUrlTitle(urlString) + "</a></td></tr>";
            }
        }
        titleString = titleString + "</table>";
        tableMap[lineno] = titleString;
        var withUL = addUL(arr[i]);
        newCode = newCode + "<span class = \"ttip\" title = \""+ lineno +"\">" + withUL + "</span>\n"
    }
    
    alert(newCode);
    $(".answer.accepted-answer").find(".post-text").find("code").html(newCode);
    
    var newCSS = GM_getResourceText ("customCSS");
    GM_addStyle(newCSS);
    
    $(".ttip").tooltip({
        content: function() {
            //var lineno = $(this).attr("title");
            //var titleText = tableMap[lineno];
            
            var text = "<h3>android.widget.Chronometer</h3>";
            text = text + "<table>";
            text = text + "<tr><td><img class = \"androidLogo\" src=\"" + androidImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><u> Javadoc </u></td><td>&nbsp;&nbsp;</td><td align = 'left'>[developer.android.com]</td></tr>";
            text = text + "<tr><td><img class = \"gitLogo\"  src=\"" + ghImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><u> Source Code </u></td><td>&nbsp;&nbsp;</td><td align = 'left'>[github.com]</td></tr></table>";
            text = text + "<table><tr><td><img class = \"SOLogo\" src=\"" + soImg + "\"></td><td align = 'left'><u>StackOverflow posts (18)</u> involving Chronometer </td></tr></table>";
            text = text + "<script> $(\".androidLogo\").css ({width: \"20px\", height: \"20px\"});\n $(\".SOLogo\").css ({width: \"20px\", height: \"20px\"});$(\".gitLogo\").css ({width: \"20px\", height: \"20px\"});</script>";
            return text;
        },
        position: {
            my: "bottom",
            at: "top"
        },
        html: true,
        open: function (event, ui) {
            ui.tooltip.css("max-width", "800px");
        }
    });
}
$("head").append("<style type=\"text/css\">u.code {border-bottom: 2px dashed #000;text-decoration: none;}</style>");
var androidImg = "http://blog.appliedis.com/wp-content/uploads/2013/11/android1.png";
var ghImg = "http://msysgit.github.io/img/git_logo.png";
var soImg = "http://files.quickmediasolutions.com/so-images/stackoverflow.svg";
var androidBaseUrl = "http://developer.android.com/reference/";
var answerId = getAnswerId();
var code = getCodeBlockText();
//var codeCss = getCodeCss();
GM_xmlhttpRequest({
    method: "GET",
    url: "http://gadget.cs.uwaterloo.ca:2145/snippet/BakerMapApiInCode.php?aid=" + answerId + "&code=" + code,
    onload: updatePage
});

//alert(codeCss);
//$(".answer.accepted-answer").find(".post-text").find("code").css(codeCss);