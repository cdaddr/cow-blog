var markdown = new Showdown.converter();

function updatePreview() {
    var mdtext = $("textarea#markdown").val();
    var htmltext = markdown.makeHtml(mdtext, true);
    $("div#preview").html(htmltext);
}

function title_to_id(title) {
    return title.toLowerCase().replace(/\s/g, '-').replace(/[^-A-Za-z0-9_]/g, '');
}

var hidden = true;
$(document).ready(function() {
        var options = {
            callback:updatePreview,
            wait:250,          // milliseconds
            highlight:false,     // highlight text on focus
            enterkey:false,     // allow "Enter" to submit data on INPUTs
        };
        $("textarea#markdown").typeWatch( options );
        
        updatePreview();

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

       $("input#title").blur(function() {
               var title = $(this).val();
               $("input#id").val(title_to_id(title));
           });
    });

