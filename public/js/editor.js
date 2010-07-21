var markdown = new Showdown.converter();

function updatePreview() {
    var mdtext = $("textarea#markdown").val();
    var htmltext = markdown.makeHtml(mdtext, true);
    $("div#preview").html(htmltext);
    SyntaxHighlighter.all();
}

function updatePermalink() {
    var title = $("input#title").val();
    if(title) {
        $("input#url").val(title
                           .toLowerCase()
                           .replace(/\s/g, '-')
                           .replace(/[^-A-Za-z0-9_]/g, '')
                          );
    } else {
        $("input#url").val('');
    }   
}

function TypeWatch(updateFn, delay) {
    var timer;
    var f = function() {
        clearTimeout(timer);
        timer = setTimeout(updateFn, delay);
    };
    f();
    return f
}

$(document).ready(function() {
    $("textarea#markdown").keyup(TypeWatch(updatePreview,250));
    $("input#title").keyup(TypeWatch(updatePermalink, 250));
     
    $("input#test").focus(function() {
        if($(this).val() == "Type this word =>") {
            $(this).val('');
        }
    });
    $("input#test").blur(function() {
        if($(this).val() == "") {
            $(this).val("Type this word =>");
        }
    });
    SyntaxHighlighter.all();

});

