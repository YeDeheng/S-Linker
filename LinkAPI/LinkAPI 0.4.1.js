// ==UserScript==
// @name         LinkAPI
// @namespace    http://tampermonkey.net/
// @version      0.4.1
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

function identifyAPI() {
    var fullText = '';
    
    [].forEach.call(
        document.querySelectorAll('.post-text p , .post-text a, #question-header .question-hyperlink'),
        function (el) {
            fullText = fullText.concat(' ').concat(el.innerText); 
        }
    );
    
    //console.log(fullText)
    
    GM_xmlhttpRequest({
        method: "POST",
        url: "http://127.0.0.1:8000/extractentity/", //localhost
        //url: "http://128.199.217.19/extract_entity/",
        data: fullText,
        headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=utf-8"
        },
        onload: function(response) {
            var entityJSON = JSON.parse(response.responseText);
            linkEntity(entityJSON);
        }
    });
}

function linkEntity(entityJSON) {  
    var myNodes = [];
    var k = 0;
    
    entityJSON.forEach(function(e) {
        toBackEndData[k] = e;
        k++;
    });

    console.log(toBackEndData);
    
    [].forEach.call(
        document.querySelectorAll('.post-text p , #question-header .question-hyperlink'),
        function (el) {
            myNodes.push(el);
        }
    );
    
     myNodes.forEach(function(e) {
        var posAfterSpan = 0;
        var posPriorSpan = 0;
        var matchLen = 0;
        var modifiedHTML = e.innerHTML;
         
        while (modifiedHTML.indexOf(entityJSON[0]) != -1) {
            var regex = new RegExp("\\b" + entityJSON[0].replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, "\\$&") + "\\b");
            //console.log(regex);
            posPriorSpan = posAfterSpan + modifiedHTML.indexOf(entityJSON[0]);
            e.innerHTML = e.innerHTML.substr(0, posAfterSpan) + e.innerHTML.substr(posAfterSpan).replace(regex, function (match) {
                matchLen = match.length;
                return "<span class='api' style='background-color: lightgreen;'>" + match + "</span>";
            });
            
            //length of text prior span tag + length of open span tag + length of matched text + length of close span tag
            posAfterSpan = posPriorSpan+56+matchLen+7; 
            modifiedHTML = e.innerHTML.substr(posAfterSpan);
            entityJSON.shift();           
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
            ui.tooltip.css("max-width", "500px");
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
    
    queryDB();
}

function UpdateTooltip(response) {
    linkJSON=JSON.parse(response.responseText);
    NumLink=Object.keys(linkJSON).length;
    
    for (var i = 0; i < NumLink; i++) {
        if(linkJSON[i]){
            contentStr = '<div>Reference: <a class="url_link" target="_blank" href="' + linkJSON[i].url + '">' + linkJSON[i].url + '</a></div>';
            LinkMap[linkJSON[i].name] = contentStr;
        }
    }
}

function queryDB() {
    GM_xmlhttpRequest({
        method: "POST",
        url: "http://127.0.0.1:8000/geturl/", //localhost
        //url: "http://128.199.217.19/geturl/",
        data: JSON.stringify(toBackEndData),
        headers: {
            "Content-Type": "application/json; charset=utf-8"
        },
        onload: UpdateTooltip
    }); 
}

$(document).ready(function(){ 
    var newjqueryCSS = GM_getResourceText("jqueryCSS");
    GM_addStyle(newjqueryCSS);

    identifyAPI();
    //setTimeout(queryDB, 1000);

    
    $(document.body).on("mouseover", ".api", function() {
        $(this).tooltip("open");
        $(this).tooltip("option", "content", function() {
            var content = LinkMap[$(this).text()];
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