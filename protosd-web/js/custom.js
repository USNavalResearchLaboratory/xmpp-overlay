
jQuery(document).ready(function($) {

    var a=$("#vtab>ul>li");
    a.mouseover(function(){
        a.removeClass("selected");
        $(this).addClass("selected");
        var b=a.index($(this));
        $("#vtab:first>div").hide().eq(b).show()}
    ).eq(0).mouseover();
});



