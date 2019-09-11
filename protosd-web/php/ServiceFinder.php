<?php

require_once __DIR__ . '/ServiceDescription.php';

/**
 * ProtoSD Service Finder
 */
class ServiceFinder {
    private $home;
    private $servicesDir;

    private $serviceList;
    private $infolist;
    private $hiddenstyle = "style=\"display: none\"";

    public function __construct() {

        if ($this->mac_server() || $this->linux_server()) {
            $this->home = "/tmp";
        } else { // windows
            $this->home = "."; // need to figure this out
        }

        if (file_exists($this->home)) { // ok, we are on a unix machine
            $this->servicesDir = $this->home."/mdns/";
        }  else { //demo mode
            $this->servicesDir = __DIR__.'/services/';
        }

        if (!is_readable($this->servicesDir)) { // demo mode
            $this->servicesDir =  __DIR__.'/services/';
        }

        $this->serviceList = array();
    }

    public function findServices() {
        $this->infolist =array();


        $this->infolist['basedir'] = $this->servicesDir;

        $this->infolist['hosts'] = "";
        $this->infolist['services'] = "";
        $this->infolist['details'] = "";

       if ($handle = opendir($this->servicesDir)) {
           $this->infolist['hosts'].= "<ul id=\"hostchoice\" class=\"hosts\">";
           while (false !== ($entry = readdir($handle))) {
                if ($this->endsWith($entry, ".services")) {
                    $filename = $this->getStringBefore($entry, ".services");

                    $hostID = ServiceDescription::getHostIdentifier($filename);

                    $this->infolist['hosts'].= "<li id=\"".$hostID."\">".$filename."</li>";

                    $contents = file_get_contents($this->servicesDir.$entry);

                    $this->infolist['services'].="<ul ". $this->hiddenstyle ." id=\"".$hostID."-show"."\" class=\"services\">";
                    $this->parseFile($contents, $hostID);
                    $this->infolist['services'].="</ul>";
                }
            }

            $this->infolist['hosts'].= "</ul>";
            closedir($handle);
        }

        if ($this->infolist['hosts'] == "")
            $this->infolist['hosts'] = "Empty";
        if ($this->infolist['services'] == "")
            $this->infolist['services'] = "Empty";
        if ($this->infolist['details'] == "")
            $this->infolist['details'] = "Empty";

        return $this->infolist;
    }

    public function parseFile($contents, $hostid) {
        $eol = $this->getEndOfLineCharacterFor($contents);
        $lines = explode($eol, $contents);

        foreach ($lines as $service) {
            if (!$this->startsWith($service, "#") ) {
                if (($service != NULL) && (trim($service)!= "")) {
                    $serdesc = new ServiceDescription($service);
                    $this->infolist['services'].= "<li id=\"".ServiceDescription::getServiceIdentifier($serdesc->getServiceName(), $hostid)."\">";
                    $this->infolist['services'].= $serdesc->getServiceName();
                    $this->infolist['services'].="</li>";

                    $this->infolist['details'].="<div ". $this->hiddenstyle ." id=\"".ServiceDescription::getServiceDetailsIdentifier($serdesc->getServiceName(), $serdesc->getHost()) ."\">";
                    $this->infolist['details'].= $serdesc->toHTML();
                    $this->infolist['details'].="</div>";
                }
            }
         }
    }


    function startsWith($haystack, $needle)
    {
        $length = strlen($needle);
        return (substr($haystack, 0, $length) === $needle);
    }

    function endsWith($haystack, $needle)
    {
        $length = strlen($needle);
        $start  = $length * -1; //negative
        return (substr($haystack, $start) === $needle);
    }

    /**
     * Get everything before the needle
     *
     * @param $haystack
     * @param $needle
     */
    function getStringBefore($haystack, $needle) {

        $pos = strpos($haystack, $needle);

        return substr($haystack, 0, $pos);
    }

    function windows_server() {
        return strpos(strtolower($_SERVER['HTTP_USER_AGENT']), "win");
    }

    function linux_server() {
        return strpos(strtolower($_SERVER['HTTP_USER_AGENT']), "linux");
    }

    function mac_server() {
        return strpos(strtolower($_SERVER['HTTP_USER_AGENT']), "mac os x");
    }


    /**
     * Gets the end of line character that is used in the provided string containgin multiple lines
     *
     * @param $string
     * @return bool
     */
    function getEndOfLineCharacterFor($string) {
        if (strpos($string, "\r\n")) // windows
            return "\r\n";
        else if (strpos($string, "\r"))  // Mac osx
            return "\r";
        else if (strpos($string, "\n")) // linux
            return "\n";
        else { // wtf
            echo "FATAL !!!! - cannot understand your csv file format - report to Ian...";
            printInstructionsAndQuit();
        }
    }
}
