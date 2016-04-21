// ==UserScript==
// @name         LinkAPI
// @namespace    http://tampermonkey.net/
// @version      0.4
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
    //var xpath = "//div[@class='post-text' or @id='question-header']//text()";
    var xpath = "//div[@class='post-text' or @id='question-header']//node()[not(ancestor::pre//span)]/text()"; //exclude code snippet
    
    //regex
    var myRe = /[A-Z]([A-Z0-9]*[a-z][a-z0-9]*[A-Z]|[a-z0-9]*[A-Z][A-Z0-9]*[a-z])[A-Za-z0-9]*/g; //complete CamelCase regex   
    //var myRe = /([A-Z][a-z0-9]+){2,}/; //simple CamelCase regex
    
    var fullText = ''
    var texts = document.evaluate(xpath, document.body, null, XPathResult.UNORDERED_NODE_SNAPSHOT_TYPE, null);
    var k = 0;
    for (n = 0; n < texts.snapshotLength; n++) {
        fullText = fullText.concat(' ').concat(textNode = texts.snapshotItem(n).textContent);  
    }
    
    GM_xmlhttpRequest({
        method: "POST",
        url: "http://127.0.0.1:8000/extractentity/", //localhost
        //url: "http://128.199.217.19/extract_entity/",
        data: fullText,
        headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=utf-8"
        },
        onload: function(response) {
            entity_json=JSON.parse(response.responseText);
        }
    });
    
    console.log(entity_json)
       
    for (n = 0; n < texts.snapshotLength; n++) {
        var textNode = texts.snapshotItem(n);
        //var str = textNode.textContent.replace(/(^[,.#$\s]+)|([,.#$\s]+$)/, '');  //remove leading and trailing symbols&whitespaces 
        myArr = textNode.textContent.match(myRe);
        if(myArr) {
            var p = textNode.parentNode;
            var frag = document.createDocumentFragment();
            var temp = textNode.nodeValue.split(myArr[0]);

            frag.appendChild(document.createTextNode(temp[0]));
            for (m = 1; m <= myArr.length; m++) {
                toBackEndData[k] = myArr[m-1];
                k++;

                var node;
                node = document.createElement('span');
                node.classList.add('api');
                node.style.backgroundColor = 'lightgreen';
                node.appendChild(document.createTextNode(myArr[m-1]));
                frag.appendChild(node);

                temp = temp[1].split(myArr[m]);
                frag.appendChild(document.createTextNode(temp[0]));             
            }
            p.replaceChild(frag, textNode);
        }
    }
    return toBackEndData;
}

function UpdateTooltip(response) {
    link_json=JSON.parse(response.responseText);
    //console.log(link_json);
    NumLink=Object.keys(link_json).length;
    
    for (var i = 0; i < NumLink; i++) {
        if(link_json[i]){
            contentStr = '<div>Reference: <a class="url_link" target="_blank" href="' + link_json[i].url + '">' + link_json[i].url + '</a></div>';
            LinkMap[link_json[i].name] = contentStr;
        }
    }
}

$(document).ready(function(){ 
    var newjqueryCSS = GM_getResourceText("jqueryCSS");
    GM_addStyle(newjqueryCSS);

    console.log(identifyAPI());
    GM_xmlhttpRequest({
        method: "POST",
        //url: "http://127.0.0.1:8000/geturl/", //localhost
        url: "http://128.199.217.19/geturl/",
        data: JSON.stringify(toBackEndData),
        headers: {
            "Content-Type": "application/json; charset=utf-8"
        },
        onload: UpdateTooltip
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

    $(".api").mouseover(function() {
        $(this).tooltip("open");
        $(this).tooltip("option", "content", function() {
            var content = LinkMap[$(this).text()];
            if(content)
                return content;
            else
                return '<div>No reference found</div>';
        });
    });

    $(".api").mouseleave(function() {
        $(this).tooltip("close");
    });
});