<?php
/**
 * A service description models an mDNS advertisement with its various parameters
 *
 * nickname-ian@XOPRoom
 * =ians-air.local-en0,
 * _mucpresence._udp,
 * local,
 * 10.0.0.22,
 * 5222,
 * PSID\=3 nick\=nickname-ian roomname\=XOPRoom affiliation\=MEMBER jidKey\=my-jid role\=PARTICPANT
 */
class ServiceDescription {

    static $COLUMN_WIDTH=32;

    private $serviceName;
    private $serviceType;
    private $domain;
    private $host;
    private $txtField;
    private $serviceAddress =null;
    private $port;

    /**
     * Format:
     * {"address":"10.0.0.26","domain":"local","hostID":"ians-air.local-en0","name":"nickname-ian@XOPRoom","port":"5222",
     * "txt":{"PSID":"3","nick":"nickname-ian","roomname":"XOPRoom",
     * "affiliation":"MEMBER","jidKey":"my-jid","role":"PARTICPANT"},
     * "type":"_mucpresence._udp"}

     *
     * @param $jsonString
     */
    function __construct($jsonString) {

        $serviceArray = json_decode($jsonString);

        $this->txtField=array();

        $this->host= $serviceArray->hostID;
        $this->serviceName = $serviceArray->name;
        $this->serviceType = $serviceArray->type;
        $this->domain = $serviceArray->domain;
        $this->serviceAddress = $serviceArray->address;
        $this->port = $serviceArray->port;

        $this->txtField= $serviceArray->txt;
    }

    private static function filterArray() {
        return array('.', '@', '/', '$', '%', '(', ')', ' ');
    }

    public static function getHostIdentifier($host) {
         $search  =  ServiceDescription::filterArray();

         $identifier = str_replace($search, "", $host);

         return $identifier;
     }

    public static function getServiceIdentifier($serviceName, $host) {
        $search  =  ServiceDescription::filterArray();

        $combined = $host."-".$serviceName;

        $identifier = str_replace($search, "", $combined);

        return $identifier;
    }

    public static function getServiceDetailsIdentifier($serviceName, $host) {
         return ServiceDescription::getServiceIdentifier($serviceName,$host)."-show";
    }

    public function toHTML() {

        $str = $this->getStartTable();
        $str.= $this->getKeyValRow("Name",$this->serviceName);
        $str.= $this->getKeyValRow("Type",$this->serviceType);
        $str.= $this->getKeyValRow("Domain",$this->domain);
        $str.= $this->getKeyValRow("Port",$this->port);
        $str.= $this->getKeyValRow("Address",$this->serviceAddress);
        $str.= $this->getRow("TXT");

        foreach ($this->txtField as $k => $v) {
            $str.= $this->getKeyValRow($k,$v);
        }

        $str.= $this->getEndTable();

        return $str;
    }

    private function lastIndexOf($string,$item){
        $index=strpos(strrev($string),strrev($item));
        if ($index){
            $index=strlen($string)-strlen($item)-$index;
            return $index;
        }
        else
            return -1;
    }

    private function getStartTable() {
        return "<table>";
    }

    private function getEndTable() {
        return "</table>";
    }

    private function getRow($name) {
        return "<tr>
                <th colspan=\"2\">".
            $name."
                </th>
                </tr>";
    }


    private function getKeyValRow($name, $val) {
        $rowspan="";
        $len=strlen($val);

        if ($len > ServiceDescription::$COLUMN_WIDTH) {
            $rowsneeded = (int)($len/ServiceDescription::$COLUMN_WIDTH);
            if ($rowsneeded*ServiceDescription::$COLUMN_WIDTH < $len) {
                $rowsneeded += 1;
            }
        }

        if ($rowsneeded==0)
            $rowsneeded=1; // always have one anyway

        $str="";

        for ($i=0; $i<$rowsneeded; ++$i) {

            $str .= "<tr>";

            if ($i==0) {
                if ($rowsneeded>1) {
                    $rsp="rowspan=".$rowsneeded;
                    $str.= "<th ". $rsp. ">". $name." </th>";
                } else {
                    $str.= "<td>". $name." </td>";
                }
            }

            $valstr = substr($val, ($i*ServiceDescription::$COLUMN_WIDTH), ServiceDescription::$COLUMN_WIDTH);

            $str .=  "<td>".
            $valstr."
                </td>
                </tr>";
        }

        return $str;

    }

    public function setDomain($domain)
    {
        $this->domain = $domain;
    }

    public function getDomain()
    {
        return $this->domain;
    }

    public function setPort($port)
    {
        $this->port = $port;
    }

    public function getPort()
    {
        return $this->port;
    }

    public function setServiceAddress($serviceAddress)
    {
        $this->serviceAddress = $serviceAddress;
    }

    public function getServiceAddress()
    {
        return $this->serviceAddress;
    }

    public function setServiceName($serviceName)
    {
        $this->serviceName = $serviceName;
    }

    public function getServiceName()
    {
        return $this->serviceName;
    }

    public function setServiceType($serviceType)
    {
        $this->serviceType = $serviceType;
    }

    public function getServiceType()
    {
        return $this->serviceType;
    }

    public function setTxtField($txtField)
    {
        $this->txtField = $txtField;
    }

    public function getTxtField()
    {
        return $this->txtField;
    }

    public function setHost($host)
    {
        $this->host = $host;
    }

    public function getHost()
    {
        return $this->host;
    }

}
