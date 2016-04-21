// ==UserScript==
// @name        BakerSOAddOn
// @namespace   JavaBaker
// @version     1
// @include     http://stackoverflow.com/*
// @include     stackoverflow.com/*
// @include     https://stackoverflow.com/*
// @require     http://code.jquery.com/jquery-1.9.1.js
// @require     http://code.jquery.com/ui/1.10.3/jquery-ui.js
// @resource    customCSS   http://code.jquery.com/ui/1.10.4/themes/smoothness/jquery-ui.css
// @updateURL http://gadget.cs.uwaterloo.ca:2145/snippet/GreasemonkeyScripts/BakerSOAddon.js
// ==/UserScript==


function getAnswerId()
{
    var aid = $(".answer.accepted-answer").attr("data-answerid");
    return aid;
}

function getCodeCss()
{
    var css = $(".answer.accepted-answer").find(".post-text").find("code").css();
    return css;
}

function getCodeBlockText()
{
    var code = $(".answer.accepted-answer").find(".post-text").find("code").first().html();
    return code;
}

function getUrlClass(element)
{
   /* var base = androidBaseUrl;
    var arr = element.split(".");
    for(var i = 0; i<arr.length; i++)
    {
        var token = arr[i];
        token = token.replace('$', '.');
        base = base + token + '/';
    }
    base = base.substring(0, base.length - 1) + ".html";
    return base;*/

    if(urlListing[element])
    {
        return urlListing[element];
    }

    else
        return "";
}

function getShortClass(element)
{
    var arr = element.split(".");
    var shortName = arr[arr.length - 1];
    shortName.replace("$",".");
    return shortName;
}

function loaded(response)
{
    console.log(response);
}

function getPostCountClass(element)
{
    var base = baseBakerCount;
    var count = "0";
    base = base + "?type=apitype&name=" + element + "&precision=1";
    /*GM_xmlhttpRequest({
    method: "GET",
    url: base,
    synchronous : true,
    onload: function (response) {
        alert(response.responseText);
        count = response.responseText;}
    });*/
    return count;
}

function getPostCountMethod(element)
{
    var base = baseBakerCount;
    var count = "0";
    base = base + "?type=method&name=" + element + "&precision=1";
    /*GM_xmlhttpRequest({
    method: "GET",
    url: base,
    synchronous : true,
    onload: function (response) {
        alert(response.responseText);
        count = response.responseText;}
    });*/
    return count;
}

function getUrlMethod(element)
{
    var classn = "";
    var arr = element.split(".");
    var flag = 0;
    for(var i = 0; i<arr.length-1; i++)
    {
        if(flag == 1)
            break;
        var token = arr[i];
        if(token.indexOf('$') !== -1)
        {
            var splitDollar = toke.split('$');
            token = splitDollar[0];
            flag = 1;
        }
        classn = classn + token + '.';
    }

    return (urlListing[classn.substr(0,classn.length-1)] + "#" + arr[arr.length-1]);
}

function getShortMethod(element)
{
    var arr = element.split(".");
    var shortName = arr[arr.length-2] + '.' + arr[arr.length - 1];
    shortName.replace("$",".");
    return shortName;
}

function getOtherSOPostsClass(element)
{
    var base = baseBaker;
    base = base + "?type=apitype&name=" + element + "&precision=1";
    return base;
}

function getOtherSOPostsMethod(element)
{
    var base = baseBaker;
    base = base + "?type=apimethod&name=" + element + "&precision=1";
    return base;
}

function updatePage(response)
{
    alert(response.responseText);
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
                urlListing[row.element] = decodeURI(row.url);
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
                urlListing[row.element] = decodeURI(row.url);
            }
            else
            {
                map2[row.line].method.push(row.element);
            }
        }
    }


    var count = cutype + 1;
    var arr = code.split('\n');
    var newCode = "";
    for(var i=0; i < arr.length; i++)
    {
        var lineno = i+1;
        var titleString = "";
        //titleString = titleString + "One line " + lineno + ": \n";
        if(map2.hasOwnProperty(i+count))
        {

            /*text = text + "<tr><td><img class = \"javaLogo\" src=\"" + javaImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><u> Javadoc </u></td><td>&nbsp;&nbsp;</td><td align = 'left'>[developer.android.com]</td></tr>";
            text = text + "<tr><td><img class = \"gitLogo\"  src=\"" + ghImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><u> Source Code </u></td><td>&nbsp;&nbsp;</td><td align = 'left'>[github.com]</td></tr></table>";
            text = text + "<table><tr><td><img class = \"SOLogo\" src=\"" + soImg + "\"></td><td align = 'left'><u>StackOverflow posts (18)</u> involving Chronometer </td></tr></table>";
            text = text + "<script> $(\".javaLogo\").css ({width: \"20px\", height: \"20px\"});\n $(\".SOLogo\").css ({width: \"20px\", height: \"20px\"});$(\".gitLogo\").css ({width: \"20px\", height: \"20px\"});</script>";
            */

            var values = map2[i+count]["class"];

            for(var j=0; j<values.length; j++)
            {
                titleString = titleString + "<h3 align = 'left'>" + values[j] + "</h3>"
                titleString = titleString + "<table>";
                //titleString = titleString + "<tr><td><img class = \"javaLogo\" src=\"" + javaImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><a href = \"" + getUrlClass(values[j]) + "\"><u>Javadoc </u></a></td><td>&nbsp;&nbsp;</td><td align = 'left'>[developer.android.com]</td></tr>";
                //titleString = titleString + "<tr><td><img class = \"SOLogo\" src=\"" + soImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><a href = \"" + getOtherSOPostsClass(values[j]) + "\"><u>Stack Overflow posts</u></a> involving " + getShortClass(values[j]) +  " </td></tr>";
                titleString = titleString + "<tr><td><img class = \"javaLogo\" src=\"" + javaImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><a href = \"" + getUrlClass(values[j]) + "\"><u>Javadoc </u></a></td><td>&nbsp;&nbsp;</td><td align = 'left'>[developer.android.com]</td></tr>";
                titleString = titleString + "<tr><td><img class = \"SOLogo\" src=\"" + soImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><a href = \"" + getOtherSOPostsClass(values[j]) + "\"><u>Stack Overflow posts</u></a> involving " + getShortClass(values[j]) +  " </td></tr>";
                titleString = titleString + "</table><br>";
            }

            var values = map2[i+count]["method"];
            if(values.length >0)
            {
                //titleString = titleString + "<h3>Methods: </h3>"
            }

            for(var j=0; j<values.length; j++)
            {
                titleString = titleString + "<h3 align = 'left'>" + values[j] + "</h3>"
                titleString = titleString + "<table>";
                titleString = titleString + "<tr><td><img class = \"javaLogo\" src=\"" + javaImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><a href = \"" + getUrlMethod(values[j]) + "\"><u> Javadoc </u></a></td><td>&nbsp;&nbsp;</td><td align = 'left'>[developer.android.com]</td></tr>";
                titleString = titleString + "<tr><td><img class = \"SOLogo\" src=\"" + soImg + "\"></td><td>&nbsp;&nbsp;</td><td align = 'left'><a href = \"" + getOtherSOPostsMethod(values[j]) + "\"><u>Stack Overflow posts</u></a> involving " + getShortMethod(values[j]) +  " </td></tr>";
                titleString = titleString + "</table><br>";
            }
            titleString = titleString + "<script> $(\".javaLogo\").css ({width: \"20px\", height: \"20px\"});\n $(\".SOLogo\").css ({width: \"20px\", height: \"20px\"});$(\".gitLogo\").css ({width: \"20px\", height: \"20px\"});</script>";
        }
        contentMap[lineno] = titleString;
        newCode = newCode + "<span class = \"ttip\" title = \""+ lineno +"\">" + arr[i] + "</span>\n"
    }

    $(".answer.accepted-answer").find(".post-text").find("code").first().html(newCode);

    var newCSS = GM_getResourceText ("customCSS");
    GM_addStyle(newCSS);



    $(".ttip").on('mouseenter',
        function(e)
        {
            $('.ttip').not($(this)).tooltip('close');   // Close all other tooltips

        }).on('mouseleave',
        function(e)
        {
            e.stopImmediatePropagation();    // keeps tooltip visible when hovering tooltip itself

        }).tooltip({
        content: function() {
            var titleText = contentMap[$(this).attr("title")];
            return titleText;
        },
        position: {
            my: "bottom-20",
            at: "top"
        },
        html: true,
        delay : 1000,
        open: function (event, ui) {
            ui.tooltip.css("max-width", "800px");
        }
    });
}



var contentMap = {};
var urlListing = {};
var baseBaker = "http://gadget.cs.uwaterloo.ca:2145/snippet/getanswers.php";
var baseBakerCount = "http://gadget.cs.uwaterloo.ca:2145/snippet/getanswerscount.php";
var javaImg = "http://www.softcrayons.com/img/javabig.png";
var ghImg = "http://msysgit.github.io/img/git_logo.png";
var soImg = "http://files.quickmediasolutions.com/so-images/stackoverflow.svg";
var androidBaseUrl = "http://developer.android.com/reference/";
var answerId = getAnswerId();
var urlToQuery = "http://gadget.cs.uwaterloo.ca:2145/snippet/BakerMapApiInCode.php?aid=" + answerId + "&code=" + code;
alert(urlToQuery);

var code = getCodeBlockText();
GM_xmlhttpRequest({
    method: "GET",
    url: urlToQuery,
    onload: updatePage
});