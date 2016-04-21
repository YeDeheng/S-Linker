// ==UserScript==
// @name         LinkAPI
// @namespace    http://tampermonkey.net/
// @version      0.5
// @description  API linking for S-NER project
// @author       Chee Yong
// @include     http://stackoverflow.com/*
// @include     stackoverflow.com/*
// @include     https://stackoverflow.com/*
// @require     https://code.jquery.com/jquery-2.1.4.min.js
// @require     https://code.jquery.com/ui/1.11.4/jquery-ui.min.js
// @resource    jqueryCSS   https://code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css
// ==/UserScript==

//'use strict';

var LinkMap = {};
var toBackEndData = {};
var myNodes = [];

function identifyAPI() {
    var fullText = '';

    [].forEach.call(
        document.querySelectorAll('.post-text p , .post-text a, #question-header .question-hyperlink'),
        // document.querySelectorAll('.post-text p , .post-text a, #question-header .question-hyperlink, .prettyprinted code'), //include code snippet
        function (el) {
            fullText = fullText.concat(' ').concat(el.textContent);
            myNodes.push(el);
        }
    );
    
    // manual mode
    // var entityJSON = ["pivot_table", "numpy.lexsort"];
    // extractEntity(entityJSON);
    
    
    GM_xmlhttpRequest({
        method: "POST",
        url: "http://127.0.0.1:8000/extractentity/", //localhost
        //url: "http://128.199.217.19/extract_entity/",
        data: fullText,
        headers: {
            "Content-Type": "application/json; charset=utf-8"
        },
        onload: function(response) {
            var entityJSON = JSON.parse(response.responseText);
            extractEntity(entityJSON);
        },
        onerror: function(response) {
            console.log("Cannot connect to server! (entity recognition)");
        },
        ontimeout: function(response) {
            console.log("Connection to server timed out! (entity recognition)");
        }
    });
    
}

function extractEntity(entityJSON) {  
    var k = 0;
    var i = 0;
    var entityList = {};
    
    console.log(entityJSON);

    entityJSON.forEach(function(e) {
        entityList[k] = e;
        k++;
    });

    toBackEndData["entity"] = entityList;

    myNodes.forEach(function(e) {
        var posAfterSpan = 0;
        var posPriorSpan = 0;
        var matchLen = 0;
        var modifiedHTML = e.innerHTML;
        var pureText = e.textContent;
        
        //var matches = e.querySelectorAll("*:not(a):not(span)");
        //console.log(matches);
        
        while (pureText.indexOf(entityJSON[0]) != -1) {
            console.log(e)
            var regex = new RegExp("\\b" + entityJSON[0].replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, "\\$&") + "\\b"); //find exact match
            posPriorSpan = posAfterSpan + modifiedHTML.indexOf(entityJSON[0]);
            e.innerHTML = e.innerHTML.substr(0, posAfterSpan) + e.innerHTML.substr(posAfterSpan).replace(regex, function (match) {
                matchLen = match.length;
                return "<span class='api' style='background-color: lightgreen;' id='"+ i + "'>" + match + "</span>";
            });

            //length of text prior span tag + length of open span tag without id + length of id + length of matched text + length of close span tag
            posAfterSpan = posPriorSpan+63+i.toString().length+matchLen+7;
            modifiedHTML = e.innerHTML.substr(posAfterSpan);
            entityJSON.shift();           
            i++;
        }
    });

    $(".api").tooltip({
        items: 'span.api',
        show: null, //show immediately
        content: '<div>No reference found</div>',
        html: true,
        //track: true,
        tooltipClass: "custom-tooltip-styling",
        position: { my: "center top+5", at: "bottom" },
        open: function(event, ui)
        {
            ui.tooltip.css("max-width", "600px");
            ui.tooltip.css("max-height", "250px");
            ui.tooltip.css("margin", "auto");
            ui.tooltip.css("font-size", "1em");
            ui.tooltip.css("padding", "5px");
            ui.tooltip.css("background-color", "#f0f0f0");
        },
        close: function(event, ui)
        {
            ui.tooltip.hover(function()
                             {
                $(this).stop(true).fadeTo(200, 1); 
            },
                             function()
                             {
                $(this).fadeOut('200', function()
                                {
                    $(this).remove();
                });
            });
        }
    });

    entityLinking();
}

function entityLinking() {
    var tagList = [];
    var allHrefs = [];

    [].forEach.call(
        document.querySelectorAll('#question .js-gps-track'),
        function (el) {
            tagList.push(el.textContent);
        }
    );
    toBackEndData["tags"] = tagList;

    [].forEach.call(
        document.querySelectorAll('.post-text a,.comment-copy a'),
        function (el) {
            // allHrefs.push({'name':el.textContent, 'url':el.href});
            allHrefs.push(el.href);
        }
    );
    toBackEndData["hrefs"] = allHrefs;

    var title = document.querySelector('#question-header');
    toBackEndData["title"] = title.textContent.trim();

    GM_xmlhttpRequest({
        method: "POST",
        url: "http://127.0.0.1:8000/linkentity/", //localhost
        //url: "http://128.199.217.19/linkentity/",
        data: JSON.stringify(toBackEndData),
        headers: {
            "Content-Type": "application/json; charset=utf-8"
        },
        onload: UpdateTooltip,
        onerror: function(response) {
            console.log("Cannot connect to server! (entity linking)");
        },
        ontimeout: function(response) {
            console.log("Connection to server timed out! (entity recognition)");
        }
    }); 
}

function UpdateTooltip(response) {
    linkJSON=JSON.parse(response.responseText);
    console.log(linkJSON)
    NumLink=Object.keys(linkJSON).length;

    for (var i = 0; i < NumLink; i++) {
        if(linkJSON[i].length > 0){
            if(linkJSON[i].length == 1) {
                contentStr = "<!DOCTYPE html><html><head><style type='text/css'>";
                contentStr += ".myTable {width:100%;word-wrap: break-word;word-break:break-all;border-spacing:0;border-collapse:collapse;} .myTable th, .myTable td {border: 1px solid black;} .table-scroll {max-width:820px;max-height:250px;overflow-y:scroll;overflow-x:hidden;}</style></head><body>";
                contentStr += "<div class='table-scroll'><table class='myTable'><colgroup><col width='40'><col max-width='350'><col width='50'></colgroup><tr><th align='left'>Rank</th><th align='left'>Reference</th><th align='left'>Library</th></tr>";
                contentStr += "<tr><td>"+ 1 + "</td><td><a href='" + linkJSON[i][0].url + "' target='_blank'>" + linkJSON[i][0].url + "</a></td><td>" + linkJSON[i][0].lib + "</td>";
                contentStr += "</table><div></body></html>";          
                LinkMap[i] = contentStr;  
            }
            else {
                //sort based on score
                linkJSON[i].sort(function(a, b){
                    return b.score - a.score;
                });
                   contentStr = "<!DOCTYPE html><html><head><style type='text/css'>"
                   contentStr += ".myTable {width:100%;word-wrap: break-word;word-break:break-all;border-spacing:0;border-collapse:collapse;} .myTable th, .myTable td {border: 1px solid black;} .table-scroll {max-width:820px;max-height:250px;overflow-y:scroll;overflow-x:hidden;}</style></head><body>"
                   contentStr += "<div class='table-scroll'><table class='myTable'><colgroup><col width='40'><col max-width='350'><col width='50'><col width='30'><col width='30'><col width='30'></colgroup><tr><th align='left'>Rank</th><th align='left'>Reference</th><th align='left'>Library</th><th align='left'>URL</th><th align='left'>Tag</th><th align='left'>Title</th></tr>";
                   for (var j = 0; j < linkJSON[i].length; j++){
                    contentStr += "<tr><td>"+ (j+1) + "</td><td><a href='" + linkJSON[i][j].url + "' target='_blank'>" + linkJSON[i][j].url + "</a></td><td>" + linkJSON[i][j].lib + "</td><td>" + linkJSON[i][j].mark[0] + "</td><td>" + linkJSON[i][j].mark[1] + "</td><td>" + linkJSON[i][j].mark[2] + "</td>";
                }
                contentStr += "</table><div></body></html>";          
                LinkMap[i] = contentStr;
            }
        }
    }
}

$(document).ready(function(){ 
    var newjqueryCSS = GM_getResourceText("jqueryCSS");
    GM_addStyle(newjqueryCSS);

    identifyAPI();

    $(document.body).on("mouseover", ".api", function() {
        $(this).tooltip("open");
        $(this).tooltip("option", "content", function() {
            var content = LinkMap[this.id];
            if(content)
                return content;
            else
                return '<div>No reference found</div>';
        });
    });

    $(document.body).on("mouseleave", ".api", function() {
        $(this).tooltip("close");
    });
});