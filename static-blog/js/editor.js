var markdown = new Showdown.converter();

function updatePreview() {
    var mdtext = $("textarea.markdown").val();
    var htmltext = markdown.makeHtml(mdtext, true);
    $("div#preview").html(htmltext);
}

var hidden = true;
$(document).ready(function() {
        var options = {
            callback:updatePreview,
            wait:250,          // milliseconds
            highlight:false,     // highlight text on focus
            enterkey:false,     // allow "Enter" to submit data on INPUTs
        };
        $("textarea.markdown").typeWatch( options );
        
        updatePreview();

        $('textarea.resizable').TextAreaResizer();

        $("div.hide").hide();
        $("div#comment-instructions a#showhide").click(function() {
                if(hidden) {
                    $("a#showhide").text("Hide help.");
                    hidden = false;
                }
                else {
                    $("a#showhide").text("Show help?");
                    hidden = true;
                }
                $("div.hide").toggle();
                return false;
            });
       $("input#test").focus(function() {
           if($(this).val() == "<= Type this word") {
               $(this).val('');
           }
       });
       $("input#test").blur(function() {
           if($(this).val() == "") {
               $(this).val("<= Type this word");
           }
       });

    });

hljs.initHighlightingOnLoad();